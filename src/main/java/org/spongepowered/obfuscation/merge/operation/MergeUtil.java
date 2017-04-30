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
import org.spongepowered.despector.ast.insn.condition.AndCondition;
import org.spongepowered.despector.ast.insn.condition.BooleanCondition;
import org.spongepowered.despector.ast.insn.condition.CompareCondition;
import org.spongepowered.despector.ast.insn.condition.Condition;
import org.spongepowered.despector.ast.insn.condition.InverseCondition;
import org.spongepowered.despector.ast.insn.condition.OrCondition;
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
import org.spongepowered.despector.ast.stmt.branch.If;
import org.spongepowered.despector.ast.stmt.branch.If.Elif;
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
import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.despector.util.TypeHelper;
import org.spongepowered.obfuscation.merge.MergeEngine;

import java.util.HashMap;
import java.util.List;
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
        if (o == null && n == null) {
            return true;
        } else if (o == null ^ n == null) {
            return false;
        }
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
            throw new IllegalStateException("Missing statement merger for " + a.getClass().getName());
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
            throw new IllegalStateException("Missing instruction merger for " + a.getClass().getName());
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
            throw new IllegalStateException("Missing condition merger for " + a.getClass().getName());
        }
        return merger.merge(set, a, b);
    }

    public static MethodEntry findMethod(TypeEntry type, String name, String desc) {
        MethodEntry mth = type.getMethod(name, desc);
        if (mth != null) {
            return mth;
        }
        if (type instanceof ClassEntry) {
            ClassEntry cls = (ClassEntry) type;
            TypeEntry sup = type.getSource().get(cls.getSuperclassName());
            if (sup != null) {
                return findMethod(sup, name, desc);
            }
        }
        return null;
    }

    public static MethodEntry findStaticMethod(TypeEntry type, String name, String desc) {
        MethodEntry mth = type.getStaticMethod(name, desc);
        if (mth != null) {
            return mth;
        }
        if (type instanceof ClassEntry) {
            ClassEntry cls = (ClassEntry) type;
            TypeEntry sup = type.getSource().get(cls.getSuperclassName());
            if (sup != null) {
                mth = findStaticMethod(sup, name, desc);
                if (mth != null) {
                    return mth;
                }
            }
        }
        for (String inter : type.getInterfaces()) {
            TypeEntry sup = type.getSource().get(inter);
            if (sup != null) {
                mth = findStaticMethod(sup, name, desc);
                if (mth != null) {
                    return mth;
                }
            }
        }
        return null;
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
            if (!merge(set, a.getValue(), b.getValue())) {
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
                if (a.getFieldName() != b.getFieldName()) {
                    return false;
                }
            } else {
                if (!set.vote(old_owner, new_owner)) {
                    return false;
                }
                FieldEntry old_field = old_owner.getField(a.getFieldName());
                FieldEntry new_field = new_owner.getField(b.getFieldName());
                if (!set.vote(old_field, new_field)) {
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
                if (a.getFieldName() != b.getFieldName()) {
                    return false;
                }
            } else {
                if (!set.vote(old_owner, new_owner)) {
                    return false;
                }
                FieldEntry old_field = old_owner.getStaticField(a.getFieldName());
                FieldEntry new_field = new_owner.getStaticField(b.getFieldName());
                if (!set.vote(old_field, new_field)) {
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
        create(If.class, (set, a, b) -> {
            if (!merge(set, a.getCondition(), b.getCondition())) {
                return false;
            }

            StatementBlock oinsn = a.getIfBody();
            StatementBlock ninsn = b.getIfBody();

            for (int i = 0; i < oinsn.getStatementCount(); i++) {
                if (i >= ninsn.getStatementCount()) {
                    return false;
                }
                if (!merge(set, oinsn.getStatement(i), ninsn.getStatement(i))) {
                    return false;
                }
            }
            if (a.getElifBlocks().size() != b.getElifBlocks().size()) {
                return false;
            }
            for (int i = 0; i < a.getElifBlocks().size(); i++) {
                Elif ae = a.getElifBlocks().get(i);
                Elif be = b.getElifBlocks().get(i);

                if (!merge(set, ae.getCondition(), be.getCondition())) {
                    return false;
                }

                StatementBlock oeinsn = ae.getBody();
                StatementBlock neinsn = be.getBody();

                for (int j = 0; j < oeinsn.getStatementCount(); j++) {
                    if (j >= neinsn.getStatementCount()) {
                        return false;
                    }
                    if (!merge(set, oeinsn.getStatement(j), neinsn.getStatement(j))) {
                        return false;
                    }
                }
            }
            if (a.getElseBlock() == null ^ a.getElseBlock() == null) {
                return false;
            }
            if (a.getElseBlock() != null) {

                StatementBlock oeinsn = a.getElseBlock().getElseBody();
                StatementBlock neinsn = b.getElseBlock().getElseBody();

                for (int j = 0; j < oeinsn.getStatementCount(); j++) {
                    if (j >= neinsn.getStatementCount()) {
                        return false;
                    }
                    if (!merge(set, oeinsn.getStatement(j), neinsn.getStatement(j))) {
                        return false;
                    }
                }
            }

            return true;
        });
        create(Switch.class, (set, a, b) -> {
            if (!merge(set, a.getSwitchVar(), b.getSwitchVar())) {
                return false;
            }
            if (a.getCases().size() != b.getCases().size()) {
                return false;
            }
            for (int i = 0; i < a.getCases().size(); i++) {
                Case ac = a.getCases().get(i);
                Case bc = b.getCases().get(i);
                if (ac.getIndices().size() != bc.getIndices().size()) {
                    return false;
                }
                if (!ac.getIndices().containsAll(bc.getIndices())) {
                    return false;
                }
                if (ac.isDefault() ^ bc.isDefault()) {
                    return false;
                }

                StatementBlock oinsn = ac.getBody();
                StatementBlock ninsn = bc.getBody();

                for (int j = 0; j < oinsn.getStatementCount(); j++) {
                    if (j >= ninsn.getStatementCount()) {
                        return false;
                    }
                    if (!merge(set, oinsn.getStatement(j), ninsn.getStatement(j))) {
                        return false;
                    }
                }
            }
            return true;
        });
        create(TryCatch.class, (set, a, b) -> {
            {
                StatementBlock oinsn = a.getTryBlock();
                StatementBlock ninsn = b.getTryBlock();

                for (int j = 0; j < oinsn.getStatementCount(); j++) {
                    if (j >= ninsn.getStatementCount()) {
                        return false;
                    }
                    if (!merge(set, oinsn.getStatement(j), ninsn.getStatement(j))) {
                        return false;
                    }
                }
            }

            if (a.getCatchBlocks().size() != b.getCatchBlocks().size()) {
                return false;
            }
            for (int j = 0; j < a.getCatchBlocks().size(); j++) {
                CatchBlock ac = a.getCatchBlocks().get(j);
                CatchBlock bc = b.getCatchBlocks().get(j);

                if (ac.getExceptions().size() != bc.getExceptions().size()) {
                    return false;
                }
                for (int k = 0; k < ac.getExceptions().size(); k++) {
                    TypeEntry ace = set.getOldSourceSet().get(ac.getExceptions().get(k));
                    TypeEntry bce = set.getNewSourceSet().get(bc.getExceptions().get(k));
                    if (ace == null || bce == null) {
                        if (!ac.getExceptions().get(k).equals(bc.getExceptions().get(k))) {
                            return false;
                        }
                    } else {
                        if (!set.vote(ace, bce)) {
                            return false;
                        }
                    }
                }

                if (!merge(set, ac.getExceptionLocal().getType(), bc.getExceptionLocal().getType())) {
                    return false;
                }

                StatementBlock oinsn = ac.getBlock();
                StatementBlock ninsn = bc.getBlock();

                for (int i = 0; i < oinsn.getStatementCount(); i++) {
                    if (i >= ninsn.getStatementCount()) {
                        return false;
                    }
                    if (!merge(set, oinsn.getStatement(i), ninsn.getStatement(i))) {
                        return false;
                    }
                }

            }

            return true;
        });
        create(While.class, (set, a, b) -> {
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
        create(DynamicInvoke.class, (set, a, b) -> {
            if (!merge(set, a.getType(), b.getType())) {
                return false;
            }
            TypeEntry ao = set.getOldSourceSet().get(a.getLambdaOwner());
            TypeEntry bo = set.getNewSourceSet().get(b.getLambdaOwner());
            if (ao == null || bo == null) {
                if (!a.getLambdaOwner().equals(b.getLambdaOwner())) {
                    return false;
                }
            } else if (!set.vote(ao, bo)) {
                return false;
            }
            return true;
        });
        create(InstanceMethodInvoke.class, (set, a, b) -> {
            TypeEntry ao = set.getOldSourceSet().get(a.getOwnerType());
            TypeEntry bo = set.getNewSourceSet().get(b.getOwnerType());
            if (ao == null || bo == null) {
                if (!a.getOwnerType().equals(b.getOwnerType())) {
                    return false;
                }
            } else {
                if (!set.vote(ao, bo)) {
                    return false;
                }
                MethodEntry old_method = findMethod(ao, a.getMethodName(), a.getMethodDescription());
                MethodEntry new_method = findMethod(bo, b.getMethodName(), b.getMethodDescription());
                if (old_method == null || new_method == null) {
                    if (a.getMethodName() != b.getMethodName()) {
                        return false;
                    }
                } else if (!set.vote(old_method, new_method)) {
                    return false;
                }
            }
            if (a.getParams().length != b.getParams().length) {
                return false;
            }
            List<String> ap = TypeHelper.splitSig(a.getMethodDescription());
            List<String> bp = TypeHelper.splitSig(b.getMethodDescription());
            for (int i = 0; i < ap.size(); i++) {
                TypeEntry apo = set.getOldSourceSet().get(ap.get(i));
                TypeEntry bpo = set.getNewSourceSet().get(bp.get(i));
                if (apo == null || bpo == null) {
                    if (!ap.get(i).equals(bp.get(i))) {
                        return false;
                    }
                } else if (!set.vote(apo, bpo)) {
                    return false;
                }
            }
            {
                String ar = TypeHelper.getRet(a.getMethodDescription());
                String br = TypeHelper.getRet(b.getMethodDescription());
                TypeEntry apo = set.getOldSourceSet().get(ar);
                TypeEntry bpo = set.getOldSourceSet().get(br);
                if (apo == null || bpo == null) {
                    if (!ar.equals(br)) {
                        return false;
                    }
                } else if (!set.vote(apo, bpo)) {
                    return false;
                }
            }
            for (int i = 0; i < a.getParams().length; i++) {
                if (!merge(set, a.getParams()[i], b.getParams()[i])) {
                    return false;
                }
            }
            if (!merge(set, a.getCallee(), b.getCallee())) {
                return false;
            }
            return true;
        });
        create(New.class, (set, a, b) -> {
            if (!merge(set, a.getType(), b.getType())) {
                return false;
            }
            if (a.getParameters().length != b.getParameters().length) {
                return false;
            }
            List<String> ap = TypeHelper.splitSig(a.getCtorDescription());
            List<String> bp = TypeHelper.splitSig(b.getCtorDescription());
            for (int i = 0; i < ap.size(); i++) {
                TypeEntry apo = set.getOldSourceSet().get(ap.get(i));
                TypeEntry bpo = set.getNewSourceSet().get(bp.get(i));
                if (apo == null || bpo == null) {
                    if (!ap.get(i).equals(bp.get(i))) {
                        return false;
                    }
                } else if (!set.vote(apo, bpo)) {
                    return false;
                }
            }
            {
                String ar = TypeHelper.getRet(a.getCtorDescription());
                String br = TypeHelper.getRet(b.getCtorDescription());
                TypeEntry apo = set.getOldSourceSet().get(ar);
                TypeEntry bpo = set.getNewSourceSet().get(br);
                if (apo == null || bpo == null) {
                    if (!ar.equals(br)) {
                        return false;
                    }
                } else if (!set.vote(apo, bpo)) {
                    return false;
                }
            }
            for (int i = 0; i < a.getParameters().length; i++) {
                if (!merge(set, a.getParameters()[i], b.getParameters()[i])) {
                    return false;
                }
            }
            return true;
        });
        create(StaticMethodInvoke.class, (set, a, b) -> {
            TypeEntry ao = set.getOldSourceSet().get(a.getOwnerType());
            TypeEntry bo = set.getOldSourceSet().get(b.getOwnerType());
            if (ao == null || bo == null) {
                if (!a.getOwnerType().equals(b.getOwnerType())) {
                    return false;
                }
            } else {
                if (!set.vote(ao, bo)) {
                    return false;
                }
                MethodEntry old_method = findStaticMethod(ao, a.getMethodName(), a.getMethodDescription());
                MethodEntry new_method = findStaticMethod(bo, b.getMethodName(), b.getMethodDescription());
                if (old_method == null || new_method == null) {
                    if (a.getMethodName() != b.getMethodName()) {
                        return false;
                    }
                } else if (!set.vote(old_method, new_method)) {
                    return false;
                }
            }
            if (a.getParams().length != b.getParams().length) {
                return false;
            }
            List<String> ap = TypeHelper.splitSig(a.getMethodDescription());
            List<String> bp = TypeHelper.splitSig(b.getMethodDescription());
            for (int i = 0; i < ap.size(); i++) {
                TypeEntry apo = set.getOldSourceSet().get(ap.get(i));
                TypeEntry bpo = set.getNewSourceSet().get(bp.get(i));
                if (apo == null || bpo == null) {
                    if (!ap.get(i).equals(bp.get(i))) {
                        return false;
                    }
                } else if (!set.vote(apo, bpo)) {
                    return false;
                }
            }
            {
                String ar = TypeHelper.getRet(a.getMethodDescription());
                String br = TypeHelper.getRet(b.getMethodDescription());
                TypeEntry apo = set.getOldSourceSet().get(ar);
                TypeEntry bpo = set.getNewSourceSet().get(br);
                if (apo == null || bpo == null) {
                    if (!ar.equals(br)) {
                        return false;
                    }
                } else if (!set.vote(apo, bpo)) {
                    return false;
                }
            }
            for (int i = 0; i < a.getParams().length; i++) {
                if (!merge(set, a.getParams()[i], b.getParams()[i])) {
                    return false;
                }
            }
            return true;
        });
        create(Comment.class, (set, a, b) -> {
            return false;
        });
        create(Increment.class, (set, a, b) -> {
            if (!merge(set, a.getLocal().getType(), b.getLocal().getType())) {
                return false;
            }
            return false;
        });
        create(Return.class, (set, a, b) -> {
            if (!a.getValue().isPresent()) {
                return !b.getValue().isPresent();
            }
            return merge(set, a.getValue().get(), b.getValue().get());
        });
        create(Throw.class, (set, a, b) -> {
            return merge(set, a.getException(), b.getException());
        });
        create(InvokeStatement.class, (set, a, b) -> {
            return merge(set, a.getInstruction(), b.getInstruction());
        });
        create(BooleanCondition.class, (set, a, b) -> {
            return merge(set, a.getConditionValue(), b.getConditionValue());
        });
        create(AndCondition.class, (set, a, b) -> {
            if (a.getOperands().size() != b.getOperands().size()) {
                return false;
            }
            for (int i = 0; i < a.getOperands().size(); i++) {
                if (!merge(set, a.getOperands().get(i), b.getOperands().get(i))) {
                    return false;
                }
            }
            return true;
        });
        create(OrCondition.class, (set, a, b) -> {
            if (a.getOperands().size() != b.getOperands().size()) {
                return false;
            }
            for (int i = 0; i < a.getOperands().size(); i++) {
                if (!merge(set, a.getOperands().get(i), b.getOperands().get(i))) {
                    return false;
                }
            }
            return true;
        });
        create(InverseCondition.class, (set, a, b) -> {
            return merge(set, a.getConditionValue(), b.getConditionValue());
        });
        create(CompareCondition.class, (set, a, b) -> {
            if (a.getOperator() != b.getOperator()) {
                return false;
            }
            if (!merge(set, a.getLeft(), b.getLeft())) {
                return false;
            }
            return merge(set, a.getRight(), b.getRight());
        });
        create(DoubleConstant.class, (set, a, b) -> true);
        create(FloatConstant.class, (set, a, b) -> true);
        create(IntConstant.class, (set, a, b) -> true);
        create(LongConstant.class, (set, a, b) -> true);
        create(NullConstant.class, (set, a, b) -> true);
        create(StringConstant.class, (set, a, b) -> true);
        create(TypeConstant.class, (set, a, b) -> {
            return merge(set, a.getConstant(), b.getConstant());
        });
        create(Cast.class, (set, a, b) -> {
            if (!merge(set, a.getType(), b.getType())) {
                return false;
            }
            return merge(set, a.getValue(), b.getValue());
        });
        create(InstanceOf.class, (set, a, b) -> {
            if (!merge(set, a.getType(), b.getType())) {
                return false;
            }
            return merge(set, a.getCheckedValue(), b.getCheckedValue());
        });
        create(MultiNewArray.class, (set, a, b) -> {
            if (!merge(set, a.getType(), b.getType())) {
                return false;
            }
            if (a.getSizes().length != b.getSizes().length) {
                return false;
            }
            for (int i = 0; i < a.getSizes().length; i++) {
                if (!merge(set, a.getSizes()[i], b.getSizes()[i])) {
                    return false;
                }
            }
            return true;
        });
        create(NewArray.class, (set, a, b) -> {
            if (!merge(set, a.getType(), b.getType())) {
                return false;
            }
            if (!merge(set, a.getSize(), b.getSize())) {
                return false;
            }
            if (a.getInitializer() == null) {
                if (b.getInitializer() != null) {
                    return false;
                }
            } else {
                if (a.getInitializer().length != b.getInitializer().length) {
                    return false;
                }
                for (int i = 0; i < a.getInitializer().length; i++) {
                    if (!merge(set, a.getInitializer()[i], b.getInitializer()[i])) {
                        return false;
                    }
                }
            }
            return true;
        });
        create(NumberCompare.class, (set, a, b) -> {
            if (!merge(set, a.getLeftOperand(), b.getLeftOperand())) {
                return false;
            }
            return merge(set, a.getRightOperand(), b.getRightOperand());
        });
        create(Ternary.class, (set, a, b) -> {
            if (!merge(set, a.getCondition(), b.getCondition())) {
                return false;
            }
            if (!merge(set, a.getTrueValue(), b.getTrueValue())) {
                return false;
            }
            return merge(set, a.getFalseValue(), b.getFalseValue());
        });
        create(NegativeOperator.class, (set, a, b) -> {
            return merge(set, a.getOperand(), b.getOperand());
        });
        create(Operator.class, (set, a, b) -> {
            if (a.getOperator() != b.getOperator()) {
                return false;
            }
            if (!merge(set, a.getLeftOperand(), b.getLeftOperand())) {
                return false;
            }
            return merge(set, a.getRightOperand(), b.getRightOperand());
        });
        create(ArrayAccess.class, (set, a, b) -> {
            if (!merge(set, a.getArrayVar(), b.getArrayVar())) {
                return false;
            }
            if (!merge(set, a.getIndex(), b.getIndex())) {
                return false;
            }
            return true;
        });
        create(InstanceFieldAccess.class, (set, a, b) -> {
            TypeEntry old_owner = set.getOldSourceSet().get(a.getOwnerName());
            TypeEntry new_owner = set.getNewSourceSet().get(b.getOwnerName());
            if (old_owner == null || new_owner == null) {
                if (a.getOwnerName() != b.getOwnerName()) {
                    return false;
                }
                return a.getFieldName() == b.getFieldName();
            }
            if (!set.vote(old_owner, new_owner)) {
                return false;
            }
            FieldEntry old_field = old_owner.getField(a.getFieldName());
            FieldEntry new_field = new_owner.getField(b.getFieldName());
            if (!set.vote(old_field, new_field)) {
                return false;
            }
            if (!merge(set, a.getFieldOwner(), b.getFieldOwner())) {
                return false;
            }
            return true;
        });
        create(LocalAccess.class, (set, a, b) -> {
            if (!merge(set, a.getLocal().getType(), b.getLocal().getType())) {
                return false;
            }
            return true;
        });
        create(StaticFieldAccess.class, (set, a, b) -> {
            TypeEntry old_owner = set.getOldSourceSet().get(a.getOwnerName());
            TypeEntry new_owner = set.getNewSourceSet().get(b.getOwnerName());
            if (old_owner == null || new_owner == null) {
                if (a.getOwnerName() != b.getOwnerName()) {
                    return false;
                }
                return a.getFieldName() == b.getFieldName();
            }
            if (!set.vote(old_owner, new_owner)) {
                return false;
            }
            FieldEntry old_field = old_owner.getStaticField(a.getFieldName());
            FieldEntry new_field = new_owner.getStaticField(b.getFieldName());
            if (!set.vote(old_field, new_field)) {
                return false;
            }
            return true;
        });
    }
}
