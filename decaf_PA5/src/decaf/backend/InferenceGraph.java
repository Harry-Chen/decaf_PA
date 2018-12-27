package decaf.backend;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

import decaf.Driver;
import decaf.dataflow.BasicBlock;
import decaf.machdesc.Register;
import decaf.tac.Tac;
import decaf.tac.Temp;


class InferenceGraph {
	public Set<Temp> nodes = new HashSet<>();
	public Map<Temp, Set<Temp>> neighbours = new HashMap<>();
	public Map<Temp, Integer> nodeDeg = new HashMap<>();
	public BasicBlock bb;
	public Register[] regs;
	public Register fp;


	private void clear() {
		nodes.clear();
		neighbours.clear();
		nodeDeg.clear();
	}


	public void alloc(BasicBlock bb, Register[] regs, Register fp) {
		this.regs = regs;
		this.bb = bb;
		this.fp = fp;
		do {
			clear();
			makeGraph();
			// For simplicity, omit handling for spilling.
		} while (!color());
	}


	private void addNode(Temp node) {
		if (nodes.contains(node)) return;
		if (node.reg != null && node.reg.equals(fp)) return;
		nodes.add(node);
		neighbours.put(node, new HashSet<Temp>());
		nodeDeg.put(node, 0);
	}


	private void removeNode(Temp n) {
		nodes.remove(n);
		for (Temp m : neighbours.get(n))
			if (nodes.contains(m))
				nodeDeg.put(m, nodeDeg.get(m) - 1);
	}


	private void addEdge(Temp a, Temp b) {
		neighbours.get(a).add(b);
		neighbours.get(b).add(a);
		nodeDeg.put(a, nodeDeg.get(a) + 1);
		nodeDeg.put(b, nodeDeg.get(b) + 1);
	}


	private boolean color() {
		if (nodes.isEmpty())
			return true;

		// Try to find a node with less than K neighbours
		Temp n = null;
		for (Temp t : nodes) {
			if (nodeDeg.get(t) < regs.length) {
				n = t;
				break;
			}
		}

		if (n != null) {
			// We've found such a node.
			removeNode(n);
			boolean subColor = color();
			n.reg = chooseAvailableRegister(n);
			return subColor;
		} else {
			throw new IllegalArgumentException(
					"Coloring with spilling is not yet supported");
		}
	}


	Register chooseAvailableRegister(Temp n) {
		Set<Register> usedRegs = new HashSet<>();
		for (Temp m : neighbours.get(n)) {
			if (m.reg == null) continue;
			usedRegs.add(m.reg);
		}
		for (Register r : regs)
			if (!usedRegs.contains(r))
				return r;
		return null;
	}


	void makeGraph() {
		// First identify all nodes. 
		// Each value is a node.
		makeNodes();
		// Then build inference edges:
		// It's your job to decide what values should be linked.
		makeEdges();
	}


	void makeNodes() {

		bb.liveUse.forEach(this::addNode);

		for (Tac tac = bb.tacList; tac != null; tac = tac.next) {
			switch (tac.opc) {
				case ADD: case SUB: case MUL: case DIV: case MOD:
				case LAND: case LOR: case GTR: case GEQ: case EQU:
				case NEQ: case LEQ: case LES:
					addNode(tac.op0); addNode(tac.op1); addNode(tac.op2);
					break;

				case NEG: case LNOT: case ASSIGN:
					addNode(tac.op0); addNode(tac.op1);
					break;

				case LOAD_VTBL: case LOAD_IMM4: case LOAD_STR_CONST:
					addNode(tac.op0);
					break;

				case INDIRECT_CALL:
					addNode(tac.op1);
				case DIRECT_CALL:
					// tac.op0 is used to hold the return value.
					// If we are calling a function with void type, then tac.op0 is null.
					if (tac.op0 != null) addNode(tac.op0);
					break;

				case PARM:
					addNode(tac.op0);
					break;

				case LOAD:
				case STORE:
					addNode(tac.op0); addNode(tac.op1);
					break;

				case BRANCH: case BEQZ: case BNEZ: case RETURN:
					throw new IllegalArgumentException();
			}
		}
	}


	// With your definition of inference graphs, build the edges.
	void makeEdges() {

		// ensure that all variables in liveUse have different colors
		for (var a : bb.liveUse) {
			for (var b : bb.liveUse) {
				if (!a.equals(b) && !neighbours.get(a).contains(b)) {
					addEdge(a, b);
				}
			}
		}

		for (Tac tac = bb.tacList; tac != null; tac = tac.next) {
			switch (tac.opc) {
				case ADD:
				case SUB:
				case MUL:
				case DIV:
				case MOD:
				case LAND:
				case LOR:
				case GTR:
				case GEQ:
				case EQU:
				case NEQ:
				case LEQ:
				case LES:
					// def op0, fallthrough
				case NEG:
				case LNOT:
				case ASSIGN:
					// def op0, fallthrough
				case LOAD_VTBL:
				case LOAD_IMM4:
				case LOAD_STR_CONST:
				case LOAD:
				case DIRECT_CALL:
				case INDIRECT_CALL:
					// def op0
					if (tac.op0 != null && tac.liveOut != null) {
						for (var out : tac.liveOut) {
							if (!out.equals(tac.op0) && nodes.contains(out)){
								addEdge(tac.op0, out);
							}
						}
					}
					break;

				case PARM:
				case STORE:
					// use op0
					break;

				case BRANCH:
				case BEQZ:
				case BNEZ:
				case RETURN:
					throw new IllegalArgumentException();
			}
		}
	}
}

