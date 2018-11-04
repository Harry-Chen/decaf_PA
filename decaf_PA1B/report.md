# 编译原理 PA 1-B 实验报告

计63 陈晟祺 2016010981

## 主要工作

本阶段的实验要求是对 Decaf 语言实现自顶向下语法分析，并支持一定程度的错误恢复。实验中，直接使用 PA1-A 的词法分析 `Lexer` 类，并提供 `pg.jar` 用于从类似 YACC 格式的语法文件中生成帮助分析的 `Table` 类。

需要完成的第一项工作是添加错误恢复，实验文档中给出了一种简单有效的思路，即基于 `Begin` 和 `End` 两个集合进行错误报告和恢复分析。这样的好处是跳过的符号较少，也避免了 `EOF` 带来的死循环问题。实际的实现基本与文档中一致，即每次进入 `Parse()` 函数时，计算 `Begin`（直接从 `Table` 获得）和 `End`（来自 Follow 和传入的 `follow` 参数），而又将 `End` 集合作为子函数调用的 `Follow` 调用传入。除此之外，还在 `Parser` 中添加了一个标志位，一旦遇到错误后，虽然语法分析继续，但不执行任何用户动作（即放弃 AST 构建），这样可以避免错误产生的 `null` 带来的各种问题。为了更好地进行调试，`Parser` 类的每一个动作（如进入分析函数、匹配 Token、分词）和状态（成功、失败、跳过）都有相应的调试打印（到标准错误）。主程序中添加了 `-d` 作为这些调试信息打印的开关，默认关闭，以防测试失败。

第二项工作是给 Decaf 的新语法特性添加相应的规则，并且需要是严格 LL(1) 的。其中对象复制、`sealed` 类、类型推导、数组迭代的规则与 PA1-A 几乎无异。串行卫士、数组常量中均有带分割的可为空的列表，需要消除原本规则中的左递归；串行卫士与普通的 If 语句有左公因子，需要消除。数组初始化、拼接都是二元运算符，需要根据优先级插入到相应的产生式（即 `Expr4` 和 `Expr5` ）之间；要注意的是二元运算符原本的产生式都是左结合的，而 `++` 是右结合的，所以需要修改语义动作（事实上右结合的 AST 构建比左结合更自然）。取子数组语句和下标动态访问都需要较高的优先级，并且与数组下标引用有左公因子 `Expr [`，因此在 `Expr8` 这一级别一并处理（其中让 `default` 以及后面的表达式成为 `ExprT8` 的一部分，是考虑到 `default` 表达式的优先级问题，使 `A[B] default C[D] default E` 被正确理解成 `(A[B] default C)[D] default E`）：

```yacc
Expr8           :   Expr9 ExprT8
ExprT8          :   '[' Expr ExprAfterBracket
                |   '.' IDENTIFIER AfterIdentExpr ExprT8
                |   /* empty */
ExprAfterBracket:   ']' ExprIsDefault
                |   ':' Expr ']' ExprT8
ExprIsDefault   :   DEFAULT Expr9 ExprT8
                |   ExprT8
```

数组生成语句由于有天然的语法边界，优先级并不重要。考虑到语义上的类似，将其与 `ArrayConstant` 这一产生式并列，产生式除了符号变化，无需修改。

## 冲突消除

本实验中使用的 `pg.jar` 工作在非严格模式下，可以处理非 LL(1) 语言中的冲突，即对于同一个非终结符，其两个产生式的预测集合非空。本实验中产生冲突的产生式是：

```yacc
IfStmt          :   IF '(' Expr ')' Stmt ElseClause
ElseClause      :   ELSE Stmt
                |   /* empty */
```

其中 $\text{PS}(ElseClause \rightarrow \text{ELSE}~Stmt) \cap \text{PS}(ElseClause \rightarrow \epsilon) = \{\text{ELSE}\}$。将其转化为程序，有如下的例子：

```java
if (A) doA(); if (B) doB(); else doC();
```

根据上述语法，无法确定这个 else 属于哪一个 if，产生了所谓的悬挂 else 问题。`pg.jar` 对此的做法是启用一个“非严格模式”，在此模式下，如果遇到冲突，规则中较前的产生式有较高的优先级。上述代码中的 else 语句在解决冲突后属于第二个 if。

## 语法改写

本次实验中将数组生成表达式的包围符号更改为 `[|` 与 `|]`，是由于如果继续使用方括号，就需要解决它和数组常量的冲突问题。但由于数组常量属于 `Constant`，它只是 `Expr` 的子集，因此不能直接用简单的提取左公因子的办法，否则会导致某些产生式作用范围的扩大（或缩小），必须较大地改动整个 `Expr` 及 `Constant` 产生式的结构才能保证非终结符的语义不变。因此将原有的语法改写成 LL(1) 是比较困难的。

## 错误误报

本次实现的错误处理会误报的一个例子为下列语句（除去了非必要的部分）

```java
[-1];
```

这是一个直观的例子，由于 `-1` 是 `Expr` 而不是 `Constant`，预期为在第二个字符报错后完成分析，但是实际情况是在第二个字符以及第四个字符均报错。涉及的产生式如下：

```yacc
Constant        :   LITERAL
                |   NULL
                |   '[' ArrayConstant ']'
ArrayConstant   :   ConstantList
                |   /* empty */
ConstantList    :   Constant ConstantListR
ConstantListR   :   ',' Constant ConstantListR
                |   /* empty */
```

这是由于当分析程序比对完 `[` 后，进入 `ArrayConstant` 的分析，`Begin` 集合为 `{ LITERAL NULL '[' ']' }`，而 `-` 导致分析出错，但其已经在 `End` 集合中（是由 `StmtBlock` 的 `Follow` 集合一路下传而来），导致程序将直接跳过 `ArrayConstant`，尝试匹配 `]`。由于没有消耗符号，预测符号依旧是 `-`，失败，程序退出 `Constant` 的分析。接下来，`-1` 被解析成一个表达式，因此 `]` 显然是多余的，导致解析器报告了第二个本不应该产生的报错。误报的原因在于 `End` 集合取的范围较大，导致解析器提前终止了跳过符号串的过程。

由此，我们应当认识到，错误报告和恢复是一个困难的问题，简单的启发式算法总会产生误报的问题。

## 正确性测试

本程序在给出的测试样例中均产生了正确的 AST 打印输出或者错误位置报告，在 PA1-A 给出的适用于此实验的测例上也得到了正确的结果。此外，PA1-A 中额外编写的测例也能被本程序正确分析。