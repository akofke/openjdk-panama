/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot.phases.aot;

import static org.graalvm.util.CollectionsUtil.anyMatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.nodes.aot.InitializeKlassNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.graph.MergeableState;
import org.graalvm.compiler.phases.graph.PostOrderNodeIterator;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.util.EconomicSet;

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaType;

public class EliminateRedundantInitializationPhase extends BasePhase<PhaseContext> {
    /**
     * Find each {@link Invoke} that has a corresponding {@link InitializeKlassNode}. These
     * {@link InitializeKlassNode} are redundant and are removed.
     *
     */
    private static void removeInitsAtStaticCalls(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.classInit() != null) {
                Node classInit = invoke.classInit();
                classInit.replaceAtUsages(null);
                graph.removeFixed((FixedWithNextNode) classInit);
            }
        }
    }

    /**
     * Remove redundant {@link InitializeKlassNode} instances from the graph.
     *
     * @param graph the program graph
     */
    private static void removeRedundantInits(StructuredGraph graph) {
        // Find and remove redundant instances of {@link InitializeKlassNode} from the graph.
        List<InitializeKlassNode> redundantInits = findRedundantInits(graph);
        for (InitializeKlassNode n : redundantInits) {
            graph.removeFixed(n);
        }
    }

    /**
     * Find {@link InitializeKlassNode} instances that can be removed because there is an existing
     * dominating initialization.
     *
     * @param graph the program graph
     */
    private static List<InitializeKlassNode> findRedundantInits(StructuredGraph graph) {
        EliminateRedundantInitializationIterator i = new EliminateRedundantInitializationIterator(graph.start(), new InitializedTypes());
        i.apply();
        return i.getRedundantInits();
    }

    /**
     * State for {@link EliminateRedundantInitializationIterator}.
     */
    private static class InitializedTypes extends MergeableState<InitializedTypes> implements Cloneable {
        private EconomicSet<ResolvedJavaType> types;

        InitializedTypes() {
            types = EconomicSet.create();
        }

        private InitializedTypes(EconomicSet<ResolvedJavaType> types) {
            this.types = types;
        }

        @Override
        public InitializedTypes clone() {
            return new InitializedTypes(EconomicSet.create(types));
        }

        public boolean contains(ResolvedJavaType type) {
            if (type.isInterface()) {
                // Check for exact match for interfaces
                return types.contains(type);
            }
            // For other types see if there is the same type or a subtype
            return anyMatch(types, t -> type.isAssignableFrom(t));
        }

        public void add(ResolvedJavaType type) {
            types.add(type);
        }

        /**
         * Merge two given types. Interfaces have to be the same to merge successfully. For other
         * types the answer is the LCA.
         *
         * @param a initialized type
         * @param b initialized type
         * @return lowest common type that is initialized if either a or b are initialized, null if
         *         no such type exists.
         */
        private static ResolvedJavaType merge(ResolvedJavaType a, ResolvedJavaType b) {
            // We want exact match for interfaces
            if (a.isInterface() || b.isInterface()) {
                if (a.equals(b)) {
                    return a;
                } else {
                    return null;
                }
            } else {
                // And LCA for other types
                ResolvedJavaType c = a.findLeastCommonAncestor(b);
                if (c.isJavaLangObject()) {
                    // Not a very useful type, always initialized, don't pollute the sets.
                    return null;
                }
                return c;
            }
        }

        /**
         * Merge two sets of types. Essentially a computation of the LCA for each element of the
         * cartesian product of the input sets. Interfaces have to match exactly.
         *
         * @param a set of initialized types
         * @param b set of initialized types
         * @return set of common types that would be initialized if types in either a or b are
         *         initialized
         */
        private static EconomicSet<ResolvedJavaType> merge(EconomicSet<ResolvedJavaType> a, EconomicSet<ResolvedJavaType> b) {
            EconomicSet<ResolvedJavaType> c = EconomicSet.create();
            for (ResolvedJavaType ta : a) {
                for (ResolvedJavaType tb : b) {
                    ResolvedJavaType tc = merge(ta, tb);
                    if (tc != null) {
                        c.add(tc);
                        if (tc.isInterface()) {
                            // Interface is not going merge with anything else, so bail out early.
                            break;
                        }
                    }
                }
            }
            return c;
        }

        @Override
        public boolean merge(AbstractMergeNode merge, List<InitializedTypes> withStates) {
            for (InitializedTypes ts : withStates) {
                types = merge(types, ts.types);
            }
            return true;
        }

        protected static String toString(EconomicSet<ResolvedJavaType> types) {
            StringBuilder b = new StringBuilder();
            b.append("[");
            Iterator<ResolvedJavaType> i = types.iterator();
            while (i.hasNext()) {
                ResolvedJavaType t = i.next();
                b.append(t.toString());
                if (i.hasNext()) {
                    b.append(",");
                }
            }
            b.append("]");
            return b.toString();
        }

        @Override
        public String toString() {
            return toString(types);
        }
    }

    /**
     * Do data flow analysis of class initializations. Collect redundant initialization nodes.
     */
    private static class EliminateRedundantInitializationIterator extends PostOrderNodeIterator<InitializedTypes> {
        private List<InitializeKlassNode> redundantInits = new ArrayList<>();

        public List<InitializeKlassNode> getRedundantInits() {
            return redundantInits;
        }

        EliminateRedundantInitializationIterator(FixedNode start, InitializedTypes initialState) {
            super(start, initialState);
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof InitializeKlassNode) {
                InitializeKlassNode i = (InitializeKlassNode) node;
                Constant c = i.value().asConstant();
                assert c != null : "Klass should be a constant at this point";
                HotSpotMetaspaceConstant klass = (HotSpotMetaspaceConstant) c;
                ResolvedJavaType t = klass.asResolvedJavaType();
                if (state.contains(t)) {
                    redundantInits.add(i);
                } else {
                    state.add(t);
                }
            }
        }

    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        removeInitsAtStaticCalls(graph);
        removeRedundantInits(graph);
    }
}
