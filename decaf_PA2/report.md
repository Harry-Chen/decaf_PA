# 编译原理 PA 1-B 实验报告

计63 陈晟祺 2016010981

## 实验简述

本阶段的实验要求是对 Decaf 新增的部分语言特性添加语义分析支持，包括符号表构建和静态语义检查两个阶段。其中符号表构建阶段主要检查各个符号的合法性、作用域等信息，并构建栈式的符号表；而语义检查阶段主要检查各语句中的表达式类型、访问权限等是否合法。最终，产生报错信息或者输出程序的符号表。

## 主要工作

### 浅复制 scopy 语句

语法为 `scopy(identifier, expr);`。

修改 `Tree.ObjectCopy` 类：

- 原本语法树中 scopy 对应阶段的 identifier 字段只是一个字符串，现在增加一个 `Tree.Ident` 类型，代表一个标识符。

修改 `TypeCheck` 类：

- 实现 `visitObjectCopy` 方法，通过访问 `identifier` 判断拷贝目标是否合法
  - 判断 `identifier` 是否为类，`identifier` 和 `expr` 类型是否相同
  - 如果 `identifier` 不合法，则检查 `expr` 是否是为类类型

至多报告两个错误，不传递错误。

### `sealed` 属性

语法为 `sealed class identfier {}`

修改 `Class` 符号类：

- 添加 `sealed` 属性。

修改 `BuildSym` 类：

- 在构建类继承关系时检查，如果父类的符号具有 `sealed` 属性，则报错并打断继承关系

至多报告这一个错误，不传递错误。

### 串行条件卫士

语法为 `if { E1:S1 || ... || En:Sn }`

修改 `BuildSym` 类：

- 逐个访问 `Si` 对应的语句块，检查符号定义并更新符号表。

修改 `TypeCheck` 类：

- 逐个检查 `Ei` 对应的条件表达式（使用预置的 `checkTestExpr` 函数
- 访问 `Si` 对应的语句块

此特性不报告特别的错误。

### 简单类型推导

语法为 `var x = Expr;`

修改`Tree.DeductedVar` 类（原本 `LValue` 中的 `var x` 会推导出此类型）：

- 添加一个 `VarDef` 对象，其附带的类型为 `TypeDeducted`

修改 `BaseType` 类：

- 添加 `UNKNOWN` 类型，表征尚未被推导类型的变量

修改 `BuildSym` 类：

- 重写对 `DeductedVar` 的访问，使其访问对应的 `VarDef` 对象
- 重写对 `TypeDeducted` 的访问，将其附带的类型初始化为 `UNKNOWN`

修改 `TypeCheck` 类：

- 修改 `visitAssign` 方法，若左侧是 `UNKNOWN` 类型，则在右侧类型正常（非 `VOID` 和 `UNKNOWN`）的情况下更改左侧符号的类型，否则报错并将左侧置为 `ERROR` 类型（即 `UNKNOWN` 不传递）。
- 修改 `checkBinaryOp` 方法，对于 `+,-,*,/`，如果左侧不是未知类型，则默认返回左侧类型，否则返回右侧类型（同样防止 `UNKNOWN` 传递）。
- 修改其他用到左值的方法，在需要的地方增加检查，对遇到的 `UNKOWN` 类型进行报错（即在语义上限定 `var x` 只能出现在赋值的左侧）。

最多报告一个错误，不传递错误。

### 数组操作

#### 数组初始化常量表达式

语法为 `E %% n`

修改 `TypeCheck` 类：

- 修改 `checkBinaryOp` 方法，对于 `%%` 这个运算符，当左右任意为错误类型时，返回错误类型。检查 `E` 的类型是否为合法数组元素，检查 `n` 的类型是否为整数，否则报告相应错误。如果产生了错误，则不再报告操作符不兼容错误。只在没有错误的情况下，将返回值的类型设置为 `E[]`，否则均为 `ERROR` 类型。

最多报告两个错误，不传递错误。

#### 数组下标动态访问表达式

语法为 `E[E1] default E'`

修改 `TypeCheck` 类：

- 重写 `visitArrayElement` 方法，先访问 `E, E1, E'`。
  - 如果 `E` 是数组类型，则判断 `E1` 是否为整数类型的，然后判断 `E'` 类型是否与 `E` 相同。无论是否发生错误，返回类型都是 `E` 的元素类型。
  - 如果 `E'` 不是数组类型，依然判断 `E1` 是否为整数类型。如果 `E'` 是有效元素类型，则返回类型与其一致，否则返回类型标记为错误（为了与样例保持一致，不报错）。

至多报告两个错误，不传递错误。

#### 数组迭代语句

语法为 `foreach (var x in E while B) S` 或 `foreach (Type x in E while B) S`

修改 `Tree.Foreach` 类：

- 添加一个 `Block` 类型的 `stmts` 对象，用于表示迭代执行的语句块。
- 初始化时，如果 `S` 本身是 `Block`，则直接赋给 `stmts`；否则，使其成为 `stmts` 的唯一语句。
- 添加一个 `associatedScope` 对象用于保存作用域。

修改 `BuildSym` 类：

- 重写 `visitForeach` 方法，在开始时创建并打开的局部作用域，手动访问 `S` 中的所有语句而不是调用 `S` 的访问，以使得 `S` 直接使用 `foreach` 的作用域。

修改 `TypeCheck` 类：

- 重写 `visitForeach` 方法，使用其附加的作用域
  - 如果 `x` 类型为未知，并且 `E` 是正确的数组类型，则赋予对应的元素类型，否则报错并赋予 `ERROR` 类型。
  - 如果 `x` 类型给定，检查 `E` 是否是正确的数组类型，如果是，检查其元素类型是否与 `x` 兼容；无论此阶段是否产生错误，`x` 的类型不发生变化。
  - 检查 `B` 是否为合法的条件语句
  - 逐个访问 `S` 的语句
  - 在进入方法时向 `breaks` 栈压入语句，并在离开时弹出，以支持 `break` 语句

语句本身（不包含 `S`）至多报告两个错误，并不传递错误。

## 遇到问题

### 报错数量

怎么报错、报多少错一定程度上是比较主观的问题。我在实现时遵循尽量多在当前语法单元内报错、少传递错误的原则，对各种可能的组合进行判断并报错，同时使用在符号表和 AST 中设置类型为 `ERROR` 的方式减少上或后续语法单元中对此的误报。但是如果返回类型可以确定，则尽量使用实际类型代替 `ERROR`类型。这一点在各类操作符连接的 `Expr` 类型中比较重要，因为 `ERROR` 的扩散也会导致后续分析中直接放弃检查，从而导致少报了应有的错。

### 报错位置

尽管不检查此项，我认为准确的报错位置依旧是重要的。对于每个错误，我都尽量在对应的 AST 节点相应的位置上进行报告（如类型标注、标识符），这样在语句较长和报错较多时，能够使信息更明确、更清晰。

### 作用域

注意到，在 `Decaf` 原本的实现中，`For, While` 等语句都有属于自己的作用域，而要求实现 `Foreach` 时，则因为可以在语句中声明绑定变量，因此也需要有作用域。实验要求中，语句附带的执行语句块的作用域与这个绑定变量相同。我认为这其实没有必要，`S` 理应有属于自己的作用域。唯一的区别是，如此之后 `S` 中可以声明与绑定变量同名的变量从而进行覆盖，这应当是编写者的自由。