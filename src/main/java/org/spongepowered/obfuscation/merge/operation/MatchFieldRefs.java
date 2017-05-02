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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.spongepowered.despector.ast.Annotation;
import org.spongepowered.despector.ast.Locals.LocalInstance;
import org.spongepowered.despector.ast.SourceSet;
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
import org.spongepowered.despector.ast.stmt.StatementVisitor;
import org.spongepowered.despector.ast.stmt.assign.ArrayAssignment;
import org.spongepowered.despector.ast.stmt.assign.InstanceFieldAssignment;
import org.spongepowered.despector.ast.stmt.assign.LocalAssignment;
import org.spongepowered.despector.ast.stmt.assign.StaticFieldAssignment;
import org.spongepowered.despector.ast.stmt.branch.Break;
import org.spongepowered.despector.ast.stmt.branch.DoWhile;
import org.spongepowered.despector.ast.stmt.branch.For;
import org.spongepowered.despector.ast.stmt.branch.ForEach;
import org.spongepowered.despector.ast.stmt.branch.If;
import org.spongepowered.despector.ast.stmt.branch.If.Elif;
import org.spongepowered.despector.ast.stmt.branch.If.Else;
import org.spongepowered.despector.ast.stmt.branch.Switch;
import org.spongepowered.despector.ast.stmt.branch.Switch.Case;
import org.spongepowered.despector.ast.stmt.branch.TryCatch;
import org.spongepowered.despector.ast.stmt.branch.TryCatch.CatchBlock;
import org.spongepowered.despector.ast.stmt.branch.While;
import org.spongepowered.despector.ast.stmt.invoke.DynamicInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InvokeStatement;
import org.spongepowered.despector.ast.stmt.invoke.New;
import org.spongepowered.despector.ast.stmt.invoke.StaticMethodInvoke;
import org.spongepowered.despector.ast.stmt.misc.Comment;
import org.spongepowered.despector.ast.stmt.misc.Increment;
import org.spongepowered.despector.ast.stmt.misc.Return;
import org.spongepowered.despector.ast.stmt.misc.Throw;
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
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MatchEntry;

import java.util.Collection;

public class MatchFieldRefs implements MergeOperation {

    private boolean prepared = false;
    private final Multimap<FieldEntry, MethodEntry> old_field_accesses = HashMultimap.create();
    private final Multimap<FieldEntry, MethodEntry> old_field_assignments = HashMultimap.create();
    private final Multimap<FieldEntry, MethodEntry> new_field_accesses = HashMultimap.create();
    private final Multimap<FieldEntry, MethodEntry> new_field_assignments = HashMultimap.create();

    private void prep(MergeEngine set) {
        FieldRefFinder new_finder = new FieldRefFinder(set.getNewSourceSet(), this.new_field_accesses, this.new_field_assignments);
        for (TypeEntry type : set.getNewSourceSet().getAllClasses()) {
            type.accept(new_finder);
        }
        FieldRefFinder old_finder = new FieldRefFinder(set.getOldSourceSet(), this.old_field_accesses, this.old_field_assignments);
        for (TypeEntry type : set.getOldSourceSet().getAllClasses()) {
            type.accept(old_finder);
        }
    }

    @Override
    public void operate(MergeEngine set) {
        if (!this.prepared) {
            this.prepared = true;
            prep(set);
        }

        for (MatchEntry match : set.getAllMatches()) {
            for (FieldEntry fld : match.getOldType().getFields()) {
                FieldMatchEntry fld_match = set.getFieldMatch(fld);
                if (fld_match == null) {
                    continue;
                }
                FieldEntry n = fld_match.getNewField();

                matchDiscreteByType(set, this.old_field_accesses.get(fld), this.new_field_accesses.get(n));
                matchDiscreteByType(set, this.old_field_assignments.get(fld), this.new_field_assignments.get(n));

            }
        }
    }

    private static void matchDiscreteByType(MergeEngine set, Collection<MethodEntry> old_accesses, Collection<MethodEntry> new_accesses) {
        Multimap<TypeEntry, MethodEntry> new_by_type = HashMultimap.create();
        for (MethodEntry mth : new_accesses) {
            TypeEntry mth_owner = set.getNewSourceSet().get(mth.getOwnerName());
            new_by_type.put(mth_owner, mth);
        }
        Multimap<TypeEntry, MethodEntry> old_by_type = HashMultimap.create();
        for (MethodEntry mth : old_accesses) {
            TypeEntry mth_owner = set.getOldSourceSet().get(mth.getOwnerName());
            old_by_type.put(mth_owner, mth);
        }

        if (new_by_type.size() == 1 && old_by_type.size() == 1) {
            TypeEntry old_type = old_by_type.keySet().iterator().next();
            TypeEntry new_type = new_by_type.keySet().iterator().next();
            set.vote(old_type, new_type);
        }

        for (TypeEntry old_type : old_by_type.keySet()) {
            Collection<MethodEntry> old_methods = old_by_type.get(old_type);
            MatchEntry type_match = set.getMatch(old_type);
            if (type_match == null) {
                continue;
            }
            TypeEntry new_type = type_match.getNewType();
            Collection<MethodEntry> new_methods = new_by_type.get(new_type);
            if (new_methods == null) {
                continue;
            }
            MatchDiscreteMethods.matchDiscrete(set, old_methods, new_methods);
        }
    }

    private static class FieldRefFinder implements InstructionVisitor, StatementVisitor, TypeVisitor {

        private SourceSet set;
        private MethodEntry current_method;
        private final Multimap<FieldEntry, MethodEntry> field_accesses;
        private final Multimap<FieldEntry, MethodEntry> field_assignments;

        public FieldRefFinder(SourceSet set, Multimap<FieldEntry, MethodEntry> field_accesses, Multimap<FieldEntry, MethodEntry> field_assignments) {
            this.set = set;
            this.field_accesses = field_accesses;
            this.field_assignments = field_assignments;
        }

        @Override
        public void visitInstanceFieldAssignment(InstanceFieldAssignment stmt) {
            TypeEntry owner = this.set.get(stmt.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findField(owner, stmt.getFieldName());
                if (fld != null) {
                    this.field_assignments.put(fld, this.current_method);
                }
            }
        }

        @Override
        public void visitStaticFieldAssignment(StaticFieldAssignment stmt) {
            TypeEntry owner = this.set.get(stmt.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findStaticField(owner, stmt.getFieldName());
                if (fld != null) {
                    this.field_assignments.put(fld, this.current_method);
                }
            }
        }

        @Override
        public void visitInstanceFieldAccess(InstanceFieldAccess insn) {
            TypeEntry owner = this.set.get(insn.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findField(owner, insn.getFieldName());
                if (fld != null) {
                    this.field_accesses.put(fld, this.current_method);
                }
            }
        }

        @Override
        public void visitStaticFieldAccess(StaticFieldAccess insn) {
            TypeEntry owner = this.set.get(insn.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findStaticField(owner, insn.getFieldName());
                if (fld != null) {
                    this.field_accesses.put(fld, this.current_method);
                }
            }
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
        public void visitStringConstant(StringConstant cst) {
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
        public void visitStaticMethodInvoke(StaticMethodInvoke insn) {
        }

        @Override
        public void visitTernary(Ternary insn) {
        }

        @Override
        public void visitTypeConstant(TypeConstant insn) {
        }

        @Override
        public void visitMultiNewArray(MultiNewArray insn) {
        }

        @Override
        public void visitArrayAssignment(ArrayAssignment arrayAssign) {
        }

        @Override
        public void visitBreak(Break breakStatement) {
        }

        @Override
        public void visitCatchBlock(CatchBlock catchBlock) {
        }

        @Override
        public void visitComment(Comment comment) {
        }

        @Override
        public void visitDoWhile(DoWhile doWhileLoop) {
        }

        @Override
        public void visitElif(Elif elseBlock) {
        }

        @Override
        public void visitElse(Else elseBlock) {
        }

        @Override
        public void visitFor(For forLoop) {
        }

        @Override
        public void visitForEach(ForEach forLoop) {
        }

        @Override
        public void visitIf(If ifBlock) {
        }

        @Override
        public void visitIncrement(Increment incrementStatement) {
        }

        @Override
        public void visitInvoke(InvokeStatement stmt) {
        }

        @Override
        public void visitLocalAssignment(LocalAssignment localAssign) {
        }

        @Override
        public void visitReturn(Return returnValue) {
        }

        @Override
        public void visitSwitch(Switch tableSwitch) {
        }

        @Override
        public void visitSwitchCase(Case case1) {
        }

        @Override
        public void visitThrow(Throw throwException) {
        }

        @Override
        public void visitTryCatch(TryCatch tryBlock) {
        }

        @Override
        public void visitWhile(While whileLoop) {
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
        public void visitField(FieldEntry fld) {
        }

        @Override
        public void visitTypeEnd() {
        }

    }

}
