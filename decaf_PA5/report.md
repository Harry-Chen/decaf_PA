# 编译原理 PA 5 实验报告

计63 陈晟祺 2016010981

## 实验简述

本阶段的实验要求是基于对三地址码（TAC）的数据流分析，实现 Decaf 编译器后端的 DU（定制-引用） 链求解功能。提供的框架中，提供了基本块构建、活跃变量分析的功能。本次的功能实现在此基础上实现。

## 主要工作

根据 PA4 的工作，我们知道，对于任意一个基本块，我们都有：
$$
\begin{equation}
\label{equation:liveness}
LiveIn[B]=LiveUse[B]\cup(LiveOut[B]-Def[B]) \\
LiveOut[B]=\bigcup_{b\in S[B]}(LiveIn)
\end{equation}
$$
而类似地，我们也可以对基本块中的 TAC 语句 $t$ 递归地定义 $LiveOut[t]$。设某个给定基本块 $B$ 的 TAC 语句按照顺序排列为 $t_1, t_2, \dots, t_n$，且其中任意一句 $t_i$ 可表示为 $v_{i0}:=f_i(v_{i1}, v_{i2},\dots,v_{im_i})$，其中 $v_{i0}$ 是被定值的变量（可以为空），后面 $m_i$ 个是被引用的变量。则我们有：
$$
\begin{equation}
LiveOut[t_i]:=
\begin{cases}
(LiveOut[t_{i+1}]\cup\{v_{(i+1)1}, v_{(i+1)2},\dots,v_{(i+1)m_{i+1}})\} \backslash \{v_{(i+1)0}\} & (i \leq n-1) \\
LiveOut[B] & (i=n)
\end{cases}
\end{equation}
$$
考虑到 $LiveOut​$ 表示在执行该语句后依旧活跃（会被用到）的变量，并且基本块中的语句都是顺序执行的，上述的定义显然是合理的。这也与框架中现有的实现是一致的。

在对于一个基本块 $B$ 建立相干图时，我们将结点集合定义为：
$$
Nodes[B]=LiveUse[B] \cup \bigcup_{i=1}^n \{v_{ij}:0\leq j \leq m_i, v_{ij}\neq\text{null}\}
$$
边集合定义为：
$$
Edges[B]=ColoredLiveUse[B]\cup \bigcup_{i=1}^n \{(v_{i0}, v'):v'\in LiveOut[t_i],~v_{i0}\neq v',~v_{i0}, v'\in Nodes[B]\}
$$
其中 $ColoredLiveUse[B]$ 表示将 $LiveUse[B]$ 对应的结点两两相连，使得这些变量不能保存在同一个（虚拟）寄存器中。而后面的部分即代表了连边准则：

> for each procedure a register-interference graph is constructed in which the nodes are symbolic registers and an edge connects two nodes if one is live at a point where the other is defined.

这里的 `live` 就表示在某个语句后还会被用到的变量，它们与该语句定值的变量显然不能保存在同一寄存器中，因此需要连接一条边，代表它们不能拥有相同的颜色。由于只需要针对 $B$ 进行计算，定义中我们忽略了 $LiveOut[t_i]$ 中不属于 $B$ 中的变量。

在代码的实现上，在对每一个基本块实现上述的相干图建立过程，并调用框架完成着色和寄存器分配后，我们还需要在每个基本块生成代码前（即最开始处）插入语句，将 $LiveOut[B]$ 中对应的变量都加载到分配后的（虚拟）寄存器中，以供下面的语句使用。

## 完整算法

上述算法只是一个简化版本，如果需要完整版本的干涉图染色寄存器分配算法，还需要如下修改：

首先需要修改染色算法，即`InferenceGraph` 类中的 `color()` 函数。如果无法找到邻居少于 $k$ 的节点（即寄存器不够用时），使用启发式算法，删去一个节点（即产生一个 spill）后继续，直到能够完成算法。伪代码为：

```java
boolean color() {
    n <- node with degress < number of registers
    if (n != null) {
        remove(n)
        subColor = color()
        n.reg = chooseReg(n)
        return subColor
    } else {
		removeRandomNode()
        color()
        return true
    }
}
```

而后需要修改寄存器分配，处理上面产生的 spill，对 `GraphColorRegisterAllocator` 类改动为：

* `findRegForRead` 函数中，如果某个变量没有关联寄存器（`t.reg == null`），就寻找一个（该语句不涉及的）寄存器 $r$，在语句生成代码前，把 $r$ 的值保存到内存中，并把 $t$ 的值加载到 $r$ 中。而后在语句的代码后，从内存中恢复 $r​$ 寄存器的原有值。
* `findRegForWrite` 函数中，如果某个变量没有关联寄存器（`t.reg == null`），可直接把对应 $t$ 的值写入到内存中。上述两个函数的修改都可以通过对 `findReg` 的修改统一进行。
* `alloc()` 函数中，在加载 `liveUse` 中的变量时，逐个判断是否分配了寄存器：如果是，则正常加载；如果不是，则无需加载。

事实上，除了能对基本块能够实现这一算法，也可对整个函数体进行实现。这样，能够消除进入和退出基本块时的寄存器保存/恢复的开销，也有全局的数据流信息，可以更高效地使用寄存器。