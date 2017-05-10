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
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MatchReferences implements MergeOperation {

    private boolean prepared = false;
    public final Multimap<FieldEntry, MethodEntry> old_field_accesses = HashMultimap.create();
    public final Multimap<FieldEntry, MethodEntry> old_field_assignments = HashMultimap.create();
    public final Multimap<FieldEntry, MethodEntry> new_field_accesses = HashMultimap.create();
    public final Multimap<FieldEntry, MethodEntry> new_field_assignments = HashMultimap.create();

    public final Multimap<String, MethodEntry> old_ext_accesses = HashMultimap.create();
    public final Multimap<String, MethodEntry> old_ext_assignments = HashMultimap.create();
    public final Multimap<String, MethodEntry> new_ext_accesses = HashMultimap.create();
    public final Multimap<String, MethodEntry> new_ext_assignments = HashMultimap.create();

    public final Multimap<MethodEntry, MethodEntry> old_method_invokes = HashMultimap.create();
    public final Multimap<MethodEntry, MethodEntry> new_method_invokes = HashMultimap.create();

    public final Multimap<String, MethodEntry> old_ext_invokes = HashMultimap.create();
    public final Multimap<String, MethodEntry> new_ext_invokes = HashMultimap.create();

    public final Multimap<TypeEntry, MethodEntry> old_inits = HashMultimap.create();
    public final Multimap<TypeEntry, MethodEntry> new_inits = HashMultimap.create();
    public final Multimap<String, MethodEntry> old_ext_inits = HashMultimap.create();
    public final Multimap<String, MethodEntry> new_ext_inits = HashMultimap.create();

    public final Multimap<TypeEntry, MethodEntry> old_array_inits = HashMultimap.create();
    public final Multimap<TypeEntry, MethodEntry> new_array_inits = HashMultimap.create();
    public final Multimap<String, MethodEntry> old_ext_array_inits = HashMultimap.create();
    public final Multimap<String, MethodEntry> new_ext_array_inits = HashMultimap.create();

    public final Map<MethodEntry, TypeEntry> old_anons = new HashMap<>();
    public final Map<MethodEntry, TypeEntry> new_anons = new HashMap<>();

    private void prep(MergeEngine set) {
        RefFinder new_finder = new RefFinder(set.getNewSourceSet(), this.new_field_accesses, this.new_field_assignments,
                this.new_ext_accesses, this.new_ext_assignments, this.new_method_invokes, this.new_ext_invokes, this.new_inits, this.new_ext_inits,
                this.new_array_inits, this.new_ext_array_inits, this.new_anons);
        for (TypeEntry type : set.getNewSourceSet().getAllClasses()) {
            type.accept(new_finder);
        }
        RefFinder old_finder = new RefFinder(set.getOldSourceSet(), this.old_field_accesses, this.old_field_assignments,
                this.old_ext_accesses, this.old_ext_assignments, this.old_method_invokes, this.old_ext_invokes, this.old_inits, this.old_ext_inits,
                this.old_array_inits, this.old_ext_array_inits, this.old_anons);
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

        for (String old_ext : this.old_ext_accesses.keySet()) {
            Collection<MethodEntry> old = this.old_ext_accesses.get(old_ext);
            Collection<MethodEntry> new_ = this.new_ext_accesses.get(old_ext);
            matchDiscreteByType(set, old, new_);
        }

        for (String old_ext : this.old_ext_assignments.keySet()) {
            Collection<MethodEntry> old = this.old_ext_assignments.get(old_ext);
            Collection<MethodEntry> new_ = this.new_ext_assignments.get(old_ext);
            matchDiscreteByType(set, old, new_);
        }

        for (String old_ext : this.old_ext_invokes.keySet()) {
            Collection<MethodEntry> old = this.old_ext_invokes.get(old_ext);
            Collection<MethodEntry> new_ = this.new_ext_invokes.get(old_ext);
            matchDiscreteByType(set, old, new_);
        }

        for (String old_ext : this.old_ext_inits.keySet()) {
            Collection<MethodEntry> old = this.old_ext_inits.get(old_ext);
            Collection<MethodEntry> new_ = this.new_ext_inits.get(old_ext);
            matchDiscreteByType(set, old, new_);
        }

        for (String old_ext : this.old_ext_array_inits.keySet()) {
            Collection<MethodEntry> old = this.old_ext_array_inits.get(old_ext);
            Collection<MethodEntry> new_ = this.new_ext_array_inits.get(old_ext);
            matchDiscreteByType(set, old, new_);
        }

        for (MatchEntry match : set.getAllMatches()) {
            {
                Collection<MethodEntry> old = this.old_inits.get(match.getOldType());
                Collection<MethodEntry> new_ = this.new_inits.get(match.getNewType());
                if (old != null && !old.isEmpty() && new_ != null && !new_.isEmpty()) {
                    matchDiscreteByType(set, old, new_);
                }
            }
            {
                Collection<MethodEntry> old = this.old_array_inits.get(match.getOldType());
                Collection<MethodEntry> new_ = this.new_array_inits.get(match.getNewType());
                if (old != null && !old.isEmpty() && new_ != null && !new_.isEmpty()) {
                    matchDiscreteByType(set, old, new_);
                }
            }
            for (FieldEntry fld : match.getOldType().getFields()) {
                FieldMatchEntry fld_match = set.getFieldMatch(fld);
                if (fld_match == null) {
                    continue;
                }
                FieldEntry n = fld_match.getNewField();

                matchDiscreteByType(set, this.old_field_accesses.get(fld), this.new_field_accesses.get(n));
                matchDiscreteByType(set, this.old_field_assignments.get(fld), this.new_field_assignments.get(n));
            }
            for (FieldEntry fld : match.getOldType().getStaticFields()) {
                FieldMatchEntry fld_match = set.getFieldMatch(fld);
                if (fld_match == null) {
                    continue;
                }
                FieldEntry n = fld_match.getNewField();

                matchDiscreteByType(set, this.old_field_accesses.get(fld), this.new_field_accesses.get(n));
                matchDiscreteByType(set, this.old_field_assignments.get(fld), this.new_field_assignments.get(n));
            }
            for (MethodEntry fld : match.getOldType().getMethods()) {
                MethodMatchEntry fld_match = set.getMethodMatch(fld);
                if (fld_match == null) {
                    continue;
                }
                MethodEntry n = fld_match.getNewMethod();

                TypeEntry oanon = this.old_anons.get(fld);
                TypeEntry nanon = this.new_anons.get(n);
                if (oanon != null && nanon != null) {
                    set.vote(oanon, nanon);
                }

                matchDiscreteByType(set, this.old_method_invokes.get(fld), this.new_method_invokes.get(n));
            }
            for (MethodEntry fld : match.getOldType().getStaticMethods()) {
                MethodMatchEntry fld_match = set.getMethodMatch(fld);
                if (fld_match == null) {
                    continue;
                }
                MethodEntry n = fld_match.getNewMethod();

                TypeEntry oanon = this.old_anons.get(fld);
                TypeEntry nanon = this.new_anons.get(n);
                if (oanon != null && nanon != null) {
                    set.vote(oanon, nanon);
                }

                matchDiscreteByType(set, this.old_method_invokes.get(fld), this.new_method_invokes.get(n));
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

    private static class RefFinder implements InstructionVisitor, StatementVisitor, TypeVisitor {

        private SourceSet set;
        private MethodEntry current_method;
        private final Multimap<FieldEntry, MethodEntry> field_accesses;
        private final Multimap<FieldEntry, MethodEntry> field_assignments;
        private final Multimap<String, MethodEntry> ext_accesses;
        private final Multimap<String, MethodEntry> ext_assignments;
        private final Multimap<MethodEntry, MethodEntry> method_invokes;
        private final Multimap<String, MethodEntry> ext_invokes;
        private final Multimap<TypeEntry, MethodEntry> inits;
        private final Multimap<String, MethodEntry> ext_inits;
        private final Multimap<TypeEntry, MethodEntry> array_inits;
        private final Multimap<String, MethodEntry> ext_array_inits;
        private final Map<MethodEntry, TypeEntry> anons;
        private boolean store_anons = false;

        public RefFinder(SourceSet set, Multimap<FieldEntry, MethodEntry> field_accesses, Multimap<FieldEntry, MethodEntry> field_assignments,
                Multimap<String, MethodEntry> ext_accesses, Multimap<String, MethodEntry> ext_assignments,
                Multimap<MethodEntry, MethodEntry> method_invokes, Multimap<String, MethodEntry> ext_invokes,
                Multimap<TypeEntry, MethodEntry> inits, Multimap<String, MethodEntry> ext_inits, Multimap<TypeEntry, MethodEntry> array_inits,
                Multimap<String, MethodEntry> ext_array_inits, Map<MethodEntry, TypeEntry> anons) {
            this.set = set;
            this.field_accesses = field_accesses;
            this.field_assignments = field_assignments;
            this.ext_accesses = ext_accesses;
            this.ext_assignments = ext_assignments;
            this.method_invokes = method_invokes;
            this.ext_invokes = ext_invokes;
            this.inits = inits;
            this.ext_inits = ext_inits;
            this.array_inits = array_inits;
            this.ext_array_inits = ext_array_inits;
            this.anons = anons;
        }

        @Override
        public void visitInstanceFieldAssignment(InstanceFieldAssignment stmt) {
            TypeEntry owner = this.set.get(stmt.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findField(owner, stmt.getFieldName());
                if (fld != null) {
                    this.field_assignments.put(fld, this.current_method);
                    return;
                }
            }
            this.ext_assignments.put(stmt.getOwnerType() + stmt.getOwnerName(), this.current_method);
        }

        @Override
        public void visitStaticFieldAssignment(StaticFieldAssignment stmt) {
            TypeEntry owner = this.set.get(stmt.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findStaticField(owner, stmt.getFieldName());
                if (fld != null) {
                    this.field_assignments.put(fld, this.current_method);
                    return;
                }
            }
            this.ext_assignments.put(stmt.getOwnerType() + stmt.getOwnerName(), this.current_method);
        }

        @Override
        public void visitInstanceFieldAccess(InstanceFieldAccess insn) {
            TypeEntry owner = this.set.get(insn.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findField(owner, insn.getFieldName());
                if (fld != null) {
                    this.field_accesses.put(fld, this.current_method);
                    return;
                }
            }
            this.ext_accesses.put(insn.getOwnerType() + insn.getOwnerName(), this.current_method);
        }

        @Override
        public void visitStaticFieldAccess(StaticFieldAccess insn) {
            TypeEntry owner = this.set.get(insn.getOwnerName());
            if (owner != null) {
                FieldEntry fld = MergeUtil.findStaticField(owner, insn.getFieldName());
                if (fld != null) {
                    this.field_accesses.put(fld, this.current_method);
                    return;
                }
            }
            this.ext_accesses.put(insn.getOwnerType() + insn.getOwnerName(), this.current_method);
        }

        @Override
        public void visitInstanceMethodInvoke(InstanceMethodInvoke insn) {
            TypeEntry owner = this.set.get(insn.getOwnerName());
            if (owner != null) {
                MethodEntry mth = MergeUtil.findMethod(owner, insn.getMethodName(), insn.getMethodDescription());
                if (mth != null) {
                    this.method_invokes.put(mth, this.current_method);
                    return;
                }
            }
            this.ext_invokes.put(insn.getOwner() + insn.getMethodName() + insn.getMethodDescription(), this.current_method);
        }

        @Override
        public void visitStaticMethodInvoke(StaticMethodInvoke insn) {
            TypeEntry owner = this.set.get(insn.getOwnerName());
            if (owner != null) {
                MethodEntry mth = MergeUtil.findStaticMethod(owner, insn.getMethodName(), insn.getMethodDescription());
                if (mth != null) {
                    this.method_invokes.put(mth, this.current_method);
                    return;
                }
            }
            this.ext_invokes.put(insn.getOwner() + insn.getMethodName() + insn.getMethodDescription(), this.current_method);
        }

        @Override
        public void visitNewArray(NewArray insn) {
            TypeEntry array = this.set.get(insn.getType().getName());
            if (array != null) {
                this.array_inits.put(array, this.current_method);
            } else {
                this.ext_array_inits.put(insn.getType().getName(), this.current_method);
            }
        }

        @Override
        public void visitMultiNewArray(MultiNewArray insn) {
            TypeEntry array = this.set.get(insn.getType().getName());
            if (array != null) {
                this.array_inits.put(array, this.current_method);
            } else {
                this.ext_array_inits.put(insn.getType().getName(), this.current_method);
            }
        }

        @Override
        public void visitNew(New insn) {
            TypeEntry type = this.set.get(insn.getType().getName());
            if (type != null) {
                this.inits.put(type, this.current_method);
                if (type.isAnonType()) {
                    if (this.store_anons) {
                        this.store_anons = false;
                        this.anons.put(this.current_method, type);
                    } else {
                        this.anons.remove(this.current_method);
                    }
                }
            } else {
                this.ext_inits.put(insn.getType().getName(), this.current_method);
            }
        }

        @Override
        public void visitMethod(MethodEntry mth) {
            this.current_method = mth;
            this.store_anons = true;
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
        public void visitNullConstant(NullConstant insn) {
        }

        @Override
        public void visitNumberCompare(NumberCompare insn) {
        }

        @Override
        public void visitOperator(Operator insn) {
        }

        @Override
        public void visitTernary(Ternary insn) {
        }

        @Override
        public void visitTypeConstant(TypeConstant insn) {
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
