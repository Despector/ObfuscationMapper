/*
 * The MIT License (MIT)
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.obfuscation.merge.operation;

import org.spongepowered.despector.ast.Annotation;
import org.spongepowered.despector.ast.Locals.LocalInstance;
import org.spongepowered.despector.ast.insn.InstructionVisitor;
import org.spongepowered.despector.ast.insn.cst.DoubleConstant;
import org.spongepowered.despector.ast.insn.cst.FloatConstant;
import org.spongepowered.despector.ast.insn.cst.IntConstant;
import org.spongepowered.despector.ast.insn.cst.LongConstant;
import org.spongepowered.despector.ast.insn.cst.NullConstant;
import org.spongepowered.despector.ast.insn.cst.StringConstant;
import org.spongepowered.despector.ast.insn.cst.TypeConstant;
import org.spongepowered.despector.ast.insn.misc.Cast;
import org.spongepowered.despector.ast.insn.misc.InstanceOf;
import org.spongepowered.despector.ast.insn.misc.MultiNewArray;
import org.spongepowered.despector.ast.insn.misc.NewArray;
import org.spongepowered.despector.ast.insn.misc.NumberCompare;
import org.spongepowered.despector.ast.insn.misc.Ternary;
import org.spongepowered.despector.ast.insn.op.NegativeOperator;
import org.spongepowered.despector.ast.insn.op.Operator;
import org.spongepowered.despector.ast.insn.var.ArrayAccess;
import org.spongepowered.despector.ast.insn.var.InstanceFieldAccess;
import org.spongepowered.despector.ast.insn.var.LocalAccess;
import org.spongepowered.despector.ast.insn.var.StaticFieldAccess;
import org.spongepowered.despector.ast.stmt.invoke.DynamicInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.New;
import org.spongepowered.despector.ast.stmt.invoke.StaticMethodInvoke;
import org.spongepowered.despector.ast.type.AnnotationEntry;
import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.EnumEntry;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.InterfaceEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.despector.ast.type.TypeVisitor;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MergeMatchedTypes implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {

        for (MatchEntry match : set.getAllMatches()) {
            if (match.isMerged()) {
                continue;
            }
            match.setAsMerged();
            TypeEntry old = match.getOldType();
            TypeEntry new_ = match.getNewType();

            if (old instanceof ClassEntry) {
                TypeEntry old_super = set.getOldSourceSet().get(((ClassEntry) old).getSuperclassName());
                TypeEntry new_super = set.getNewSourceSet().get(((ClassEntry) new_).getSuperclassName());
                if (old_super != null && new_super != null) {
                    set.vote(old_super, new_super);
                }
            }

            {
                Map<TypeEntry, Integer> old_interface_complexities = new HashMap<>();
                for (String inter : old.getInterfaces()) {
                    TypeEntry intr = set.getOldSourceSet().get(inter);
                    if (intr == null) {
                        continue;
                    }
                    old_interface_complexities.put(intr, intr.getMethodCount());
                }
                Map<TypeEntry, Integer> new_interface_complexities = new HashMap<>();
                for (String inter : new_.getInterfaces()) {
                    TypeEntry intr = set.getNewSourceSet().get(inter);
                    if (intr == null) {
                        continue;
                    }
                    new_interface_complexities.put(intr, intr.getMethodCount());
                }

                for (TypeEntry old_inter : old_interface_complexities.keySet()) {
                    if (set.isTypeMatched(old_inter)) {
                        continue;
                    }
                    int cmp = old_interface_complexities.get(old_inter);
                    TypeEntry inter_match = null;
                    for (Map.Entry<TypeEntry, Integer> e : new_interface_complexities.entrySet()) {
                        if (set.isTypeMatched(e.getKey())) {
                            continue;
                        }
                        int delta = Math.abs(cmp - e.getValue());
                        if (delta <= 2) {
                            if (inter_match != null) {
                                inter_match = null;
                                break;
                            }
                            inter_match = e.getKey();
                        } else if (delta < 6) {
                            inter_match = null;
                            break;
                        }
                    }
                    if (inter_match != null) {
                        set.vote(old_inter, inter_match);
                    }
                }
            }

            {
                // This is looking for types (particularily functional
                // interfaces) that only have a single non-synthetic method as
                // we can safely match those together
                MethodEntry old_non_syn = null;
                for (MethodEntry mth : old.getMethods()) {
                    if (mth.isSynthetic()) {
                        continue;
                    }
                    if (old_non_syn != null) {
                        old_non_syn = null;
                        break;
                    }
                    old_non_syn = mth;
                }
                MethodEntry new_non_syn = null;
                for (MethodEntry mth : new_.getMethods()) {
                    if (mth.isSynthetic()) {
                        continue;
                    }
                    if (new_non_syn != null) {
                        new_non_syn = null;
                        break;
                    }
                    new_non_syn = mth;
                }

                if (old_non_syn != null && new_non_syn != null) {
                    MethodMatchEntry m = set.getPendingMethodMatch(old_non_syn);
                    m.setNewMethod(new_non_syn);
                    set.setAsMatched(m);
                }
            }

            {
                // this is finding unique string constants on a by-method basis
                // to attempt matching using them
                StringConstantMethodWalker walker = new StringConstantMethodWalker();
                old.accept(walker);
                Map<String, MethodEntry> old_unique = walker.getUnique();
                walker = new StringConstantMethodWalker();
                new_.accept(walker);
                Map<String, MethodEntry> new_unique = walker.getUnique();

                for (Map.Entry<String, MethodEntry> e : old_unique.entrySet()) {
                    MethodEntry n = new_unique.get(e.getKey());
                    if (n != null) {
                        set.vote(e.getValue(), n);
                    }
                }
            }

        }

    }

    private static final class StringConstantMethodWalker implements InstructionVisitor, TypeVisitor {

        private Map<String, MethodEntry> unique = new HashMap<>();
        private Set<String> non_unique = new HashSet<>();
        private MethodEntry current_method = null;

        public StringConstantMethodWalker() {

        }

        public Map<String, MethodEntry> getUnique() {
            return this.unique;
        }

        @Override
        public void visitClassEntry(ClassEntry type) {
        }

        @Override
        public void visitEnumEntry(EnumEntry type) {
        }

        @Override
        public void visitInterfaceEntry(InterfaceEntry type) {
        }

        @Override
        public void visitAnnotationEntry(AnnotationEntry type) {
        }

        @Override
        public void visitAnnotation(Annotation annotation) {
        }

        @Override
        public void visitMethod(MethodEntry mth) {
            this.current_method = mth;
        }

        @Override
        public void visitMethodEnd() {
            this.current_method = null;
        }

        @Override
        public void visitField(FieldEntry fld) {
        }

        @Override
        public void visitTypeEnd() {
        }

        @Override
        public void visitArrayAccess(ArrayAccess insn) {
        }

        @Override
        public void visitCast(Cast insn) {
        }

        @Override
        public void visitDoubleConstant(DoubleConstant insn) {
        }

        @Override
        public void visitDynamicInvoke(DynamicInvoke insn) {
        }

        @Override
        public void visitFloatConstant(FloatConstant insn) {
        }

        @Override
        public void visitInstanceFieldAccess(InstanceFieldAccess insn) {
        }

        @Override
        public void visitInstanceMethodInvoke(InstanceMethodInvoke insn) {
        }

        @Override
        public void visitInstanceOf(InstanceOf insn) {
        }

        @Override
        public void visitIntConstant(IntConstant insn) {
        }

        @Override
        public void visitLocalAccess(LocalAccess insn) {
        }

        @Override
        public void visitLocalInstance(LocalInstance local) {
        }

        @Override
        public void visitLongConstant(LongConstant insn) {
        }

        @Override
        public void visitMultiNewArray(MultiNewArray insn) {
        }

        @Override
        public void visitNegativeOperator(NegativeOperator insn) {
        }

        @Override
        public void visitNew(New insn) {
        }

        @Override
        public void visitNewArray(NewArray insn) {
        }

        @Override
        public void visitNullConstant(NullConstant insn) {
        }

        @Override
        public void visitNumberCompare(NumberCompare insn) {
        }

        @Override
        public void visitOperator(Operator insn) {
        }

        @Override
        public void visitStaticFieldAccess(StaticFieldAccess insn) {
        }

        @Override
        public void visitStaticMethodInvoke(StaticMethodInvoke insn) {
        }

        @Override
        public void visitStringConstant(StringConstant cst) {
            if (this.non_unique.contains(cst.getConstant())) {
                return;
            }
            MethodEntry mth = this.unique.get(cst.getConstant());
            if (mth == null) {
                this.unique.put(cst.getConstant(), this.current_method);
            } else if (mth != this.current_method) {
                this.non_unique.add(cst.getConstant());
                this.unique.remove(cst.getConstant());
            }
        }

        @Override
        public void visitTernary(Ternary insn) {
        }

        @Override
        public void visitTypeConstant(TypeConstant insn) {
        }

    }

}
