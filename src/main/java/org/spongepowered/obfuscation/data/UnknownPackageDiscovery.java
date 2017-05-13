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
package org.spongepowered.obfuscation.data;

import org.spongepowered.despector.ast.AccessModifier;
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
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InvokeStatement;
import org.spongepowered.despector.ast.stmt.invoke.Lambda;
import org.spongepowered.despector.ast.stmt.invoke.MethodReference;
import org.spongepowered.despector.ast.stmt.invoke.New;
import org.spongepowered.despector.ast.stmt.invoke.StaticMethodInvoke;
import org.spongepowered.despector.ast.stmt.misc.Comment;
import org.spongepowered.despector.ast.stmt.misc.Increment;
import org.spongepowered.despector.ast.stmt.misc.Return;
import org.spongepowered.despector.ast.stmt.misc.Throw;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.operation.MatchReferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UnknownPackageDiscovery {

    private final MergeEngine engine;
    private Map<TypeEntry, String> packages = new HashMap<>();

    public UnknownPackageDiscovery(MergeEngine engine) {
        this.engine = engine;
    }

    public void build() {
        outer: for (TypeEntry type : this.engine.getNewSourceSet().getAllClasses()) {
            String mapped = this.engine.getNewMappings().mapType(type.getName());
            if (mapped != null) {
                String pkg = mapped.substring(0, mapped.lastIndexOf('/'));
                this.packages.put(type, pkg);
            } else {
                PackageFinder finder = new PackageFinder(type);
                type.accept(finder);
                Set<TypeEntry> refs = finder.getReferences();
                for (TypeEntry ref : refs) {
                    String refs_mapped = this.engine.getNewMappings().mapType(ref.getName());
                    if (refs_mapped != null) {
                        String pkg = refs_mapped.substring(0, refs_mapped.lastIndexOf('/'));
                        this.packages.put(type, pkg);
                        continue outer;
                    }
                }
                refs = findOtherReferences(this.engine, type);
                for (TypeEntry ref : refs) {
                    String refs_mapped = this.engine.getNewMappings().mapType(ref.getName());
                    if (refs_mapped != null) {
                        String pkg = refs_mapped.substring(0, refs_mapped.lastIndexOf('/'));
                        this.packages.put(type, pkg);
                        continue outer;
                    }
                }
            }
        }
    }

    private Set<TypeEntry> findOtherReferences(MergeEngine engine, TypeEntry type) {
        SourceSet set = type.getSource();
        MatchReferences refs = engine.getOperation(MatchReferences.class);
        Set<TypeEntry> types = new HashSet<>();
        for (MethodEntry mth : type.getMethods()) {
            if (mth.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                if (!mth.getName().equals("<init>")) {
                    for (MethodEntry ref : refs.new_method_invokes.get(mth)) {
                        TypeEntry owner = set.get(ref.getOwnerName());
                        if (owner != null && owner != type) {
                            types.add(owner);
                        }
                    }
                }
            }
        }
        for (MethodEntry mth : type.getStaticMethods()) {
            if (mth.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                for (MethodEntry ref : refs.new_method_invokes.get(mth)) {
                    TypeEntry owner = set.get(ref.getOwnerName());
                    if (owner != null && owner != type) {
                        types.add(owner);
                    }
                }
            }
        }
        for (FieldEntry fld : type.getFields()) {
            if (fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                for (MethodEntry ref : refs.new_field_accesses.get(fld)) {
                    TypeEntry owner = set.get(ref.getOwnerName());
                    if (owner != null && owner != type) {
                        types.add(owner);
                    }
                }
                for (MethodEntry ref : refs.new_field_assignments.get(fld)) {
                    TypeEntry owner = set.get(ref.getOwnerName());
                    if (owner != null && owner != type) {
                        types.add(owner);
                    }
                }
            }
        }
        for (FieldEntry fld : type.getStaticFields()) {
            if (fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                for (MethodEntry ref : refs.new_field_accesses.get(fld)) {
                    TypeEntry owner = set.get(ref.getOwnerName());
                    if (owner != null && owner != type) {
                        types.add(owner);
                    }
                }
                for (MethodEntry ref : refs.new_field_assignments.get(fld)) {
                    TypeEntry owner = set.get(ref.getOwnerName());
                    if (owner != null && owner != type) {
                        types.add(owner);
                    }
                }
            }
        }
        for (MethodEntry ref : refs.new_inits.get(type)) {
            TypeEntry owner = set.get(ref.getOwnerName());
            if (owner != null && owner != type) {
                types.add(owner);
            }
        }
        return types;
    }

    public String getPackage(TypeEntry type) {
        String pkg = this.packages.get(type);
        if (pkg == null) {
            return "net/minecraft/unknown";
        }
        return pkg;
    }

    private static class PackageFinder implements StatementVisitor, InstructionVisitor {

        private SourceSet set;
        private TypeEntry current_type;
        private Set<TypeEntry> references = new HashSet<>();

        public PackageFinder(TypeEntry type) {
            this.current_type = type;
            this.set = type.getSource();
        }

        public Set<TypeEntry> getReferences() {
            return this.references;
        }

        @Override
        public void visitInstanceFieldAccess(InstanceFieldAccess insn) {
            if (!insn.getOwnerName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(insn.getOwnerName());
                if (owner != null) {
                    FieldEntry fld = owner.getField(insn.getFieldName());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
        }

        @Override
        public void visitInstanceMethodInvoke(InstanceMethodInvoke insn) {
            if (!insn.getOwnerName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(insn.getOwnerName());
                if (owner != null) {
                    MethodEntry fld = owner.getMethod(insn.getMethodName(), insn.getMethodDescription());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
        }

        @Override
        public void visitNew(New insn) {
            if (!insn.getType().getName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(insn.getType().getName());
                if (owner != null) {
                    MethodEntry fld = owner.getMethod("<init>", insn.getCtorDescription());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
        }

        @Override
        public void visitStaticMethodInvoke(StaticMethodInvoke insn) {
            if (!insn.getOwnerName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(insn.getOwnerName());
                if (owner != null) {
                    MethodEntry fld = owner.getStaticMethod(insn.getMethodName(), insn.getMethodDescription());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
        }

        @Override
        public void visitStaticFieldAccess(StaticFieldAccess insn) {
            if (!insn.getOwnerName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(insn.getOwnerName());
                if (owner != null) {
                    FieldEntry fld = owner.getStaticField(insn.getFieldName());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
        }

        @Override
        public void visitInstanceFieldAssignment(InstanceFieldAssignment stmt) {
            if (!stmt.getOwnerName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(stmt.getOwnerName());
                if (owner != null) {
                    FieldEntry fld = owner.getField(stmt.getFieldName());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
        }

        @Override
        public void visitStaticFieldAssignment(StaticFieldAssignment stmt) {
            if (!stmt.getOwnerName().equals(this.current_type.getName())) {
                TypeEntry owner = this.set.get(stmt.getOwnerName());
                if (owner != null) {
                    FieldEntry fld = owner.getStaticField(stmt.getFieldName());
                    if (fld != null && fld.getAccessModifier() == AccessModifier.PACKAGE_PRIVATE) {
                        this.references.add(owner);
                    }
                }
            }
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
        public void visitDynamicInvoke(Lambda insn) {
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
        public void visitMultiNewArray(MultiNewArray insn) {
        }

        @Override
        public void visitNegativeOperator(NegativeOperator insn) {
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
        public void visitStringConstant(StringConstant insn) {
        }

        @Override
        public void visitTernary(Ternary insn) {
        }

        @Override
        public void visitTypeConstant(TypeConstant insn) {
        }

        @Override
        public void visitArrayAssignment(ArrayAssignment stmt) {
        }

        @Override
        public void visitBreak(Break stmt) {
        }

        @Override
        public void visitCatchBlock(CatchBlock stmt) {
        }

        @Override
        public void visitComment(Comment stmt) {
        }

        @Override
        public void visitDoWhile(DoWhile stmt) {
        }

        @Override
        public void visitElif(Elif stmt) {
        }

        @Override
        public void visitElse(Else stmt) {
        }

        @Override
        public void visitFor(For stmt) {
        }

        @Override
        public void visitForEach(ForEach stmt) {
        }

        @Override
        public void visitIf(If stmt) {
        }

        @Override
        public void visitIncrement(Increment stmt) {
        }

        @Override
        public void visitInvoke(InvokeStatement stmt) {
        }

        @Override
        public void visitLocalAssignment(LocalAssignment stmt) {
        }

        @Override
        public void visitReturn(Return stmt) {
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
        public void visitMethodReference(MethodReference methodReference) {
            // TODO Auto-generated method stub
            
        }

    }

}
