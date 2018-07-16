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

import org.spongepowered.despector.ast.Annotation;
import org.spongepowered.despector.ast.generic.VoidTypeSignature;
import org.spongepowered.despector.ast.insn.cst.StringConstant;
import org.spongepowered.despector.ast.stmt.Statement;
import org.spongepowered.despector.ast.stmt.StatementBlock;
import org.spongepowered.despector.ast.stmt.assign.StaticFieldAssignment;
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InvokeStatement;
import org.spongepowered.despector.ast.stmt.invoke.New;
import org.spongepowered.despector.ast.stmt.misc.Return;
import org.spongepowered.despector.ast.type.AnnotationEntry;
import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.EnumEntry;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.InterfaceEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.despector.ast.type.TypeVisitor;
import org.spongepowered.despector.util.TypeHelper;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodGroup;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.HashMap;
import java.util.Map;

public class UnknownMemberMapper implements TypeVisitor {

    private final MappingsSet mappings;
    private final MergeEngine engine;
    private final MappingsSet previous;

    private final Map<MethodEntry, MethodEntry> synthetic_overloads = new HashMap<>();

    private Map<FieldEntry, String> enum_names = new HashMap<>();
    private EnumEntry current_type;
    private int next_type = 0;

    public UnknownMemberMapper(MappingsSet set, MergeEngine engine, MappingsSet prev) {
        this.mappings = set;
        this.engine = engine;
        this.previous = prev;
    }

    public int getNext() {
        return this.next_type;
    }

    private void findSyntheticOverloads(TypeEntry type) {
        for (MethodEntry mth : type.getMethods()) {
            if (!mth.isSynthetic() || mth.getInstructions() == null) {
                continue;
            }
            StatementBlock insns = mth.getInstructions();
            MethodEntry target = null;
            if (mth.getReturnType() == VoidTypeSignature.VOID) {
                if (insns.getStatementCount() != 2) {
                    continue;
                }
                Statement a = insns.getStatement(0);
                if (!(a instanceof InvokeStatement) || !(((InvokeStatement) a).getInstruction() instanceof InstanceMethodInvoke)) {
                    continue;
                }
                InstanceMethodInvoke invoke = (InstanceMethodInvoke) ((InvokeStatement) a).getInstruction();
                if (!invoke.getOwnerName().equals(type.getName())) {
                    continue;
                }
                target = type.getMethod(invoke.getMethodName(), invoke.getMethodDescription());
            } else {
                if (insns.getStatementCount() != 1) {
                    continue;
                }
                Statement a = insns.getStatement(0);
                if (!(a instanceof Return) || !(((Return) a).getValue().get() instanceof InstanceMethodInvoke)) {
                    continue;
                }
                InstanceMethodInvoke invoke = (InstanceMethodInvoke) ((Return) a).getValue().get();
                if (!invoke.getOwnerName().equals(type.getName())) {
                    continue;
                }
                target = type.getMethod(invoke.getMethodName(), invoke.getMethodDescription());
            }
            if (target == null) {
                continue;
            }
            this.synthetic_overloads.put(target, mth);
        }
    }

    @Override
    public void visitClassEntry(ClassEntry type) {
        findSyntheticOverloads(type);
    }

    @Override
    public void visitEnumEntry(EnumEntry type) {
        findSyntheticOverloads(type);
        this.enum_names = null;
        this.current_type = type;
    }

    @Override
    public void visitInterfaceEntry(InterfaceEntry type) {
        findSyntheticOverloads(type);
    }

    @Override
    public void visitAnnotationEntry(AnnotationEntry type) {
        findSyntheticOverloads(type);
    }

    @Override
    public void visitAnnotation(Annotation annotation) {
    }

    private String getMapped(MethodEntry mth) {
        String mapped = this.mappings.mapMethod(mth.getOwnerName(), mth.getName(), mth.getDescription());
        if (mapped != null) {
            return mapped;
        }
        MethodGroup group = this.engine.getNewMethodGroup(mth);
        for (MethodEntry g : group.getMethods()) {
            mapped = this.mappings.mapMethod(g.getOwnerName(), g.getName(), g.getDescription());
            if (mapped != null) {
                return mapped;
            }
        }
        MethodMatchEntry match = this.engine.getMethodMatchInverse(mth);
        if (match != null && match.getOldMethod() instanceof MergeEngine.DummyMethod) {
            return match.getOldMethod().getName();
        }
        MethodEntry intr = this.synthetic_overloads.get(mth);
        if (intr != null) {
            return getMapped(intr);
        }
        if (mth.getName().length() > 2) {
            return null;
        }
        if (mapped == null && this.previous != null) {
            String prev_mapped_owner = this.previous.mapType(mth.getOwnerName());
            String mapped_owner = this.mappings.mapType(mth.getOwnerName());
            if (mapped_owner.equals(prev_mapped_owner)) {
                String prev = this.previous.mapMethod(mth.getOwnerName(), mth.getName(), mth.getDescription());
                String inv = this.mappings.inverseMethod(mapped_owner, prev, mth.getDescription());
                if (prev != null && inv == null) {
                    return prev;
                }
            }
        }
        mapped = String.format("mth_%04d_%s", this.next_type++, mth.getName());
        return mapped;
    }

    @Override
    public void visitMethod(MethodEntry mth) {
        if (mth.getName().equals("<init>") || mth.getName().equals("<clinit>")) {
            return;
        }
        String mapped = getMapped(mth);
        if (mapped != null) {
            this.mappings.addMethodMapping(mth.getOwnerName(), mth.getName(), mth.getDescription(), mapped);
        }
    }

    @Override
    public void visitMethodEnd() {
    }

    @Override
    public void visitField(FieldEntry fld) {
        String mapped = this.mappings.mapField(fld.getOwnerName(), fld.getName());
        if (mapped == null) {
            FieldMatchEntry match = this.engine.getFieldMatchInverse(fld);
            if (match != null && match.getOldField() instanceof MergeEngine.DummyField) {
                mapped = match.getOldField().getName();
            } else {
                if (this.current_type != null) {
                    checkEnumConstants();
                    mapped = this.enum_names.get(fld);
                }
                if (mapped == null && this.previous != null) {
                    String prev_mapped_owner = this.previous.mapField(fld.getOwnerName());
                    String mapped_owner = this.mappings.mapType(fld.getOwnerName());
                    if (mapped_owner.equals(prev_mapped_owner)) {
                        String prev = this.previous.mapField(fld.getOwnerName(), fld.getName());
                        String inv = this.mappings.inverseField(mapped_owner, prev);
                        if (prev != null && inv == null) {
                            mapped = prev;
                        }
                    }
                }
                if (mapped == null) {
                    mapped = String.format("fld_%04d_%s", this.next_type++, fld.getName());
                }
            }
            this.mappings.addFieldMapping(fld.getOwnerName(), fld.getName(), mapped);
        }
    }

    private void checkEnumConstants() {
        if (this.enum_names == null) {
            this.enum_names = new HashMap<>();
            MethodEntry clinit = this.current_type.getStaticMethod("<clinit>");
            if(clinit == null) {
                return;
            }
            for (Statement stmt : clinit.getInstructions()) {
                if (!(stmt instanceof StaticFieldAssignment)) {
                    break;
                }
                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                if (assign.getFieldName().contains("$VALUES")) {
                    continue;
                }
                if (!TypeHelper.descToType(assign.getOwnerType()).equals(this.current_type.getName()) || !(assign.getValue() instanceof New)) {
                    break;
                }
                FieldEntry assigned = this.current_type.getStaticField(assign.getFieldName());
                if (assigned == null) {
                    continue;
                }
                New n = (New) assign.getValue();
                String name = ((StringConstant) n.getParameters()[0]).getConstant();
                this.enum_names.put(assigned, name);
            }
        }
    }

    @Override
    public void visitTypeEnd() {
        this.current_type = null;
    }

}
