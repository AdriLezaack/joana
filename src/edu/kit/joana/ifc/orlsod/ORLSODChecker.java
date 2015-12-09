package edu.kit.joana.ifc.orlsod;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.slicer.conc.I2PBackward;
import edu.kit.joana.ifc.sdg.lattice.IStaticLattice;
import edu.kit.joana.ifc.sdg.lattice.LatticeUtil;

public class ORLSODChecker<L> {

	/** the lattice which provides the security levels we annotate nodes with */
	protected IStaticLattice<L> secLattice;

	/** the SDG we want to check */
	protected SDG sdg;

	/** user-provided annotations */
	protected Map<SDGNode, L> userAnn;

	/** maps each node to its so-called <i>probabilistic influencers</i> */
	protected ProbInfComputer probInf;

	/** classification which is computed in a fixed-point iteration */
	protected Map<SDGNode, L> cl;

	public ORLSODChecker(SDG sdg, IStaticLattice<L> secLattice, Map<SDGNode, L> userAnn, ProbInfComputer probInf) {
		this.sdg = sdg;
		this.secLattice = secLattice;
		this.userAnn = userAnn;
		this.probInf = probInf;
	}

	protected Map<SDGNode, L> initCL(boolean incorporateUserAnns) {
		Map<SDGNode, L> ret = new HashMap<SDGNode, L>();
		for (SDGNode n : sdg.vertexSet()) {
			if (incorporateUserAnns && userAnn.containsKey(n)) {
				ret.put(n, userAnn.get(n));
			} else {
				ret.put(n, secLattice.getBottom());
			}
		}
		return ret;
	}

	public int check() {
		I2PBackward backw = new I2PBackward(sdg);
		// 1.) initialize classification: we go from the bottom up, so every node is classified as low initially
		// except for the user annotated nodes: They are classified with the level given by the user
		cl = initCL(true);
		// 2.) fixed-point iteration
		int numIters = 0;
		boolean change;
		do {
			change = false;
			for (SDGNode n : sdg.vertexSet()) {
				L oldLevel = cl.get(n);
				// nothing changes if current level is top already
				if (secLattice.getTop().equals(oldLevel)) continue;
				L newLevel = oldLevel;
				// 2a.) propagate from backward slice
				System.out.println(String.format("BS(%s) = %s", n, backw.slice(n)));
				for (SDGNode m : backw.slice(n)) {
					newLevel = secLattice.leastUpperBound(newLevel, cl.get(m));
					if (secLattice.getTop().equals(newLevel)) {
						break; // we can abort the loop here - level cannot get any higher
					}
				}
				// 2b.) propagate security levels from probabilistic influencers
				Collection<? extends SDGNode> pi = probInf.getProbabilisticInfluencers(n);
				System.out.println(String.format("ProbInf(%s) = %s", n, pi));
				for (SDGNode cp : pi) {
					newLevel = secLattice.leastUpperBound(newLevel, cl.get(cp));
					if (secLattice.getTop().equals(newLevel)) {
						break; // we can abort the loop here - level cannot get any higher
					}
				}
				if (!newLevel.equals(oldLevel)) {
					cl.put(n, newLevel);
					change = true;
				}
			}
			numIters++;
		} while (change);
		System.out.println(String.format("needed %d iteration(s).", numIters));
		// 3.) check that sink levels comply
		return checkCompliance();
	}

	protected final int checkCompliance() {
		boolean compliant = true;
		int noViolations = 0;
		for (Map.Entry<SDGNode, L> userAnnEntry : userAnn.entrySet()) {
			SDGNode s = userAnnEntry.getKey();
			L userLvl = userAnnEntry.getValue();
			if (!LatticeUtil.isLeq(secLattice, cl.get(s), userLvl)) {
				System.out.println("Violation at node " + s + ": user-annotated level is " + userLvl + ", computed level is " + cl.get(s));
				noViolations++;
				compliant = false;
			}
		}
		if (compliant) {
			System.out.println("no violations found.");
		}
		return noViolations;
	}
}
