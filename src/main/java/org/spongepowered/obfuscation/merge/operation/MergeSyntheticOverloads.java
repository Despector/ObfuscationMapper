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

import org.spongepowered.despector.ast.generic.VoidTypeSignature;
import org.spongepowered.despector.ast.insn.misc.NewArray;
import org.spongepowered.despector.ast.stmt.Statement;
import org.spongepowered.despector.ast.stmt.StatementBlock;
import org.spongepowered.despector.ast.stmt.assign.StaticFieldAssignment;
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InvokeStatement;
import org.spongepowered.despector.ast.stmt.misc.Return;
import org.spongepowered.despector.ast.type.EnumEntry;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

public class MergeSyntheticOverloads implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {
        for (TypeEntry type : set.getNewSourceSet().getAllClasses()) {
            if (type instanceof EnumEntry) {
                if (!type.isAnonType()) {
                    MethodEntry clinit = type.getStaticMethod("<clinit>");
                    if (clinit != null && clinit.getInstructions() != null) {
                        for (Statement stmt : clinit.getInstructions()) {
                            if (stmt instanceof StaticFieldAssignment) {
                                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                                if (!assign.getOwnerName().equals(type.getName())) {
                                    continue;
                                }
                                if (assign.getValue() instanceof NewArray) {
                                    FieldEntry fld = type.getStaticField(assign.getFieldName());
                                    if (fld != null && fld.getType().getDescriptor().startsWith("[")) {
                                        FieldEntry dummy =
                                                MergeEngine.createDummyField(set.getOldSourceSet(), "$VALUES", fld.getType(), type.getName());
                                        FieldMatchEntry match = set.getPendingFieldMatch(dummy);
                                        match.setNewField(fld);
                                        set.setAsMatched(match);
                                        set.incrementChangeCount();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (MethodEntry mth : type.getMethods()) {
                if (!mth.isSynthetic() || mth.getInstructions() == null || mth.getName().length() <= 2) {
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
                MethodEntry dummy = MergeEngine.createDummyMethod(set.getOldSourceSet(), mth.getName(), target.getDescription(), type.getName());
                MethodMatchEntry match = set.getPendingMethodMatch(dummy);
                match.setNewMethod(target);
                set.setAsMatched(match);
                set.incrementChangeCount();
            }

        }

    }

}
