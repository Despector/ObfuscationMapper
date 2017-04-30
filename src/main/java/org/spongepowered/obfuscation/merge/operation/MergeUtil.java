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

import org.spongepowered.despector.ast.generic.ClassTypeSignature;
import org.spongepowered.despector.ast.generic.GenericClassTypeSignature;
import org.spongepowered.despector.ast.generic.TypeArgument;
import org.spongepowered.despector.ast.generic.TypeSignature;
import org.spongepowered.despector.ast.insn.Instruction;
import org.spongepowered.despector.ast.insn.condition.Condition;
import org.spongepowered.despector.ast.stmt.Statement;
import org.spongepowered.despector.ast.stmt.StatementBlock;
import org.spongepowered.despector.ast.stmt.assign.ArrayAssignment;
import org.spongepowered.despector.ast.stmt.assign.InstanceFieldAssignment;
import org.spongepowered.despector.ast.stmt.assign.LocalAssignment;
import org.spongepowered.despector.ast.stmt.assign.StaticFieldAssignment;
import org.spongepowered.despector.ast.stmt.branch.Break;
import org.spongepowered.despector.ast.stmt.branch.DoWhile;
import org.spongepowered.despector.ast.stmt.branch.For;
import org.spongepowered.despector.ast.stmt.branch.ForEach;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;

import java.util.HashMap;
import java.util.Map;

public class MergeUtil {

    public static void merge(MergeEngine set, FieldEntry o, FieldEntry n) {
        merge(set, o.getType(), n.getType());
    }

    public static void merge(MergeEngine set, MethodEntry o, MethodEntry n) {
        merge(set, o.getReturnType(), n.getReturnType());
        if (o.getParamTypes().size() == n.getParamTypes().size()) {
            for (int i = 0; i < o.getParamTypes().size(); i++) {
                merge(set, o.getParamTypes().get(i), n.getParamTypes().get(i));
            }
        }

        StatementBlock oinsn = o.getInstructions();
        StatementBlock ninsn = n.getInstructions();

        for (int i = 0; i < oinsn.getStatementCount(); i++) {
            if (i >= ninsn.getStatementCount()) {
                break;
            }
            if (!merge(set, oinsn.getStatement(i), ninsn.getStatement(i))) {
                break;
            }
        }

    }

    public static boolean merge(MergeEngine set, TypeSignature o, TypeSignature n) {
        if (o instanceof ClassTypeSignature && n instanceof ClassTypeSignature) {
            ClassTypeSignature c = (ClassTypeSignature) o;
            ClassTypeSignature cn = (ClassTypeSignature) n;
            TypeEntry old_type = set.getOldSourceSet().get(c.getDescriptor());
            TypeEntry new_type = set.getNewSourceSet().get(cn.getDescriptor());
            if (old_type == null || new_type == null) {
                return c.getDescriptor() == cn.getDescriptor();
            }
            return set.vote(old_type, new_type);
        } else if (o instanceof GenericClassTypeSignature && n instanceof GenericClassTypeSignature) {
            GenericClassTypeSignature c = (GenericClassTypeSignature) o;
            GenericClassTypeSignature cn = (GenericClassTypeSignature) n;
            if (c.getArguments().size() != cn.getArguments().size()) {
                return false;
            }
            TypeEntry old_type = set.getOldSourceSet().get(c.getDescriptor());
            TypeEntry new_type = set.getNewSourceSet().get(cn.getDescriptor());
            if ((old_type == null || new_type == null) && c.getDescriptor() != cn.getDescriptor()) {
                return false;
            } else if (!set.vote(old_type, new_type)) {
                return false;
            }
            for (int i = 0; i < c.getArguments().size(); i++) {
                TypeArgument old_arg = c.getArguments().get(i);
                TypeArgument new_arg = c.getArguments().get(i);
                if (old_arg.getWildcard() != new_arg.getWildcard()) {
                    return false;
                }
                if (!merge(set, old_arg.getSignature(), new_arg.getSignature())) {
                    return false;
                }
            }
            return true;
        }
        return o.getClass() == n.getClass();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean merge(MergeEngine set, Statement a, Statement b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null ^ b == null) {
            return false;
        }
        if (a.getClass() != b.getClass()) {
            return false;
        }
        StatementMerger merger = statement_mergers.get(a.getClass());
        if (merger == null) {
            return false; // temporary
            // throw new IllegalStateException("Missing statement merger for " +
            // a.getClass().getName());
        }
        return merger.merge(set, a, b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean merge(MergeEngine set, Instruction a, Instruction b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null ^ b == null) {
            return false;
        }
        if (a.getClass() != b.getClass()) {
            return false;
        }
        InstructionMerger merger = instruction_mergers.get(a.getClass());
        if (merger == null) {
            return false; // temporary
            // throw new IllegalStateException("Missing instruction merger for "
            // + a.getClass().getName());
        }
        return merger.merge(set, a, b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean merge(MergeEngine set, Condition a, Condition b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null ^ b == null) {
            return false;
        }
        if (a.getClass() != b.getClass()) {
            return false;
        }
        ConditionMerger merger = condition_mergers.get(a.getClass());
        if (merger == null) {
            return false; // temporary
            // throw new IllegalStateException("Missing condition merger for " +
            // a.getClass().getName());
        }
        return merger.merge(set, a, b);
    }

    private static final Map<Class<?>, StatementMerger<?>> statement_mergers = new HashMap<>();
    private static final Map<Class<?>, InstructionMerger<?>> instruction_mergers = new HashMap<>();
    private static final Map<Class<?>, ConditionMerger<?>> condition_mergers = new HashMap<>();

    @FunctionalInterface
    private static interface StatementMerger<S extends Statement> {

        boolean merge(MergeEngine set, S a, S b);

    }

    private static <S extends Statement> void create(Class<S> type, StatementMerger<S> merger) {
        statement_mergers.put(type, merger);
    }

    @FunctionalInterface
    private static interface InstructionMerger<S extends Instruction> {

        boolean merge(MergeEngine set, S a, S b);

    }

    private static <S extends Instruction> void create(Class<S> type, InstructionMerger<S> merger) {
        instruction_mergers.put(type, merger);
    }

    @FunctionalInterface
    private static interface ConditionMerger<S extends Condition> {

        boolean merge(MergeEngine set, S a, S b);

    }

    private static <S extends Condition> void create(Class<S> type, ConditionMerger<S> merger) {
        condition_mergers.put(type, merger);
    }

    static {
        create(ArrayAssignment.class, (set, a, b) -> {
            if (!merge(set, a.getArray(), b.getArray())) {
                return false;
            }
            if (!merge(set, a.getIndex(), b.getIndex())) {
                return false;
            }
            return true;
        });
        create(InstanceFieldAssignment.class, (set, a, b) -> {
            TypeEntry old_owner = set.getOldSourceSet().get(a.getOwnerName());
            TypeEntry new_owner = set.getNewSourceSet().get(b.getOwnerName());
            if (old_owner == null || new_owner == null) {
                if (a.getOwnerName() != b.getOwnerName()) {
                    return false;
                }
            } else {
                if (!set.vote(old_owner, new_owner)) {
                    return false;
                }
                FieldEntry old_field = old_owner.getField(a.getFieldName());
                FieldEntry new_field = new_owner.getField(b.getFieldName());
                if (!set.getFieldMatch(old_field).vote(new_field)) {
                    return false;
                }
            }
            if (!merge(set, a.getOwner(), b.getOwner())) {
                return false;
            }
            if (!merge(set, a.getValue(), b.getValue())) {
                return false;
            }
            return true;
        });
        create(LocalAssignment.class, (set, a, b) -> {
            if (!merge(set, a.getLocal().getType(), b.getLocal().getType())) {
                return false;
            }
            if (!merge(set, a.getValue(), b.getValue())) {
                return false;
            }
            return true;
        });
        create(StaticFieldAssignment.class, (set, a, b) -> {
            TypeEntry old_owner = set.getOldSourceSet().get(a.getOwnerName());
            TypeEntry new_owner = set.getNewSourceSet().get(b.getOwnerName());
            if (old_owner == null || new_owner == null) {
                if (a.getOwnerName() != b.getOwnerName()) {
                    return false;
                }
            } else {
                if (!set.vote(old_owner, new_owner)) {
                    return false;
                }
                FieldEntry old_field = old_owner.getStaticField(a.getFieldName());
                FieldEntry new_field = new_owner.getStaticField(b.getFieldName());
                if (!set.getFieldMatch(old_field).vote(new_field)) {
                    return false;
                }
            }
            if (!merge(set, a.getValue(), b.getValue())) {
                return false;
            }
            return true;
        });
        create(Break.class, (set, a, b) -> {
            return true;
        });
        create(DoWhile.class, (set, a, b) -> {
            if (!merge(set, a.getCondition(), b.getCondition())) {
                return false;
            }

            StatementBlock oinsn = a.getBody();
            StatementBlock ninsn = b.getBody();

            for (int i = 0; i < oinsn.getStatementCount(); i++) {
                if (i >= ninsn.getStatementCount()) {
                    return false;
                }
                if (!merge(set, oinsn.getStatement(i), ninsn.getStatement(i))) {
                    return false;
                }
            }
            return true;
        });
        create(For.class, (set, a, b) -> {
            if (!merge(set, a.getInit(), b.getInit())) {
                return false;
            }
            if (!merge(set, a.getCondition(), b.getCondition())) {
                return false;
            }
            if (!merge(set, a.getIncr(), b.getIncr())) {
                return false;
            }

            StatementBlock oinsn = a.getBody();
            StatementBlock ninsn = b.getBody();

            for (int i = 0; i < oinsn.getStatementCount(); i++) {
                if (i >= ninsn.getStatementCount()) {
                    return false;
                }
                if (!merge(set, oinsn.getStatement(i), ninsn.getStatement(i))) {
                    return false;
                }
            }
            return true;
        });
        create(ForEach.class, (set, a, b) -> {
            if (!merge(set, a.getCollectionValue(), b.getCollectionValue())) {
                return false;
            }

            StatementBlock oinsn = a.getBody();
            StatementBlock ninsn = b.getBody();

            for (int i = 0; i < oinsn.getStatementCount(); i++) {
                if (i >= ninsn.getStatementCount()) {
                    return false;
                }
                if (!merge(set, oinsn.getStatement(i), ninsn.getStatement(i))) {
                    return false;
                }
            }
            return true;
        });
    }

}
