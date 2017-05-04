/*
 * This file is part of SpongeAPI, licensed under the MIT License (MIT).
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
package org.spongepowered.obfuscation.util;

import com.google.common.collect.Multimap;
import org.spongepowered.despector.ast.SourceSet;
import org.spongepowered.despector.ast.generic.ClassSignature;
import org.spongepowered.despector.ast.generic.GenericClassTypeSignature;
import org.spongepowered.despector.ast.generic.MethodSignature;
import org.spongepowered.despector.ast.generic.TypeParameter;
import org.spongepowered.despector.ast.generic.TypeSignature;
import org.spongepowered.despector.ast.generic.TypeVariableSignature;
import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.data.MethodGroup;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MethodGroupBuilder {

    private final SourceSet set;
    private final Map<MethodEntry, MethodGroup> groups;
    private final Multimap<TypeEntry, TypeEntry> subtypes;

    private final Set<TypeEntry> handled = new HashSet<>();

    public MethodGroupBuilder(SourceSet set, Map<MethodEntry, MethodGroup> groups, Multimap<TypeEntry, TypeEntry> subtypes) {
        this.set = set;
        this.groups = groups;
        this.subtypes = subtypes;
    }

    public void build() {
        for (TypeEntry type : this.set.getAllClasses()) {
            process(type);
        }
    }

    private void process(TypeEntry type) {
        if (this.handled.contains(type)) {
            return;
        }
        this.handled.add(type);
        if (type instanceof ClassEntry) {
            TypeEntry supr = this.set.get(((ClassEntry) type).getSuperclassName());
            if (supr != null) {
                process(supr);
            }
        }
        for (String intr : type.getInterfaces()) {
            TypeEntry intrface = this.set.get(intr);
            if (intrface != null) {
                process(intrface);
            }
        }

        for (MethodEntry mth : type.getMethods()) {
            if (this.groups.get(mth) != null) {
                continue;
            }
            MethodGroup group = new MethodGroup(mth);
            this.groups.put(mth, group);
            if (!mth.getName().equals("<init>") && !mth.getName().equals("<clinit>")) {
                for (TypeEntry sub : this.subtypes.get(type)) {
                    discoverOverrides(sub, mth.getName(), mth.getDescription(), createSubtypeSignature(sub, type, mth.getMethodSignature()), group);
                }
            }
        }
    }

    private MethodSignature createSubtypeSignature(TypeEntry sub, TypeEntry owner, MethodSignature sig) {
        GenericClassTypeSignature super_sig = null;
        ClassSignature clssig = sub.getSignature();
        if (clssig.getSuperclassSignature().getName().equals(owner.getName())) {
            super_sig = clssig.getSuperclassSignature();
        } else {
            for (GenericClassTypeSignature intr : clssig.getInterfaceSignatures()) {
                if (intr.getName().equals(owner.getName())) {
                    super_sig = intr;
                }
            }
        }
        if (super_sig == null) {
            throw new IllegalStateException();
        }

        MethodSignature subsig = new MethodSignature();
        subsig.setReturnType(sig.getReturnType());
        subsig.getThrowsSignature().addAll(sig.getThrowsSignature());
        subsig.getTypeParameters().addAll(sig.getTypeParameters());
        outer: for (int i = 0; i < sig.getParameters().size(); i++) {
            TypeSignature n = sig.getParameters().get(i);
            if (n instanceof TypeVariableSignature) {
                String var = ((TypeVariableSignature) n).getIdentifier();
                var = var.substring(1, var.length() - 1);
                int j = 0;
                for (; j < owner.getSignature().getParameters().size(); j++) {
                    TypeParameter param = owner.getSignature().getParameters().get(j);
                    if (param.getIdentifier().equals(var)) {
                        subsig.getParameters().add(super_sig.getArguments().get(j).getSignature());
                        continue outer;
                    }
                }
            }
            subsig.getParameters().add(n);
        }
        return subsig;
    }

    private void discoverOverrides(TypeEntry next, String name, String desc, MethodSignature sig, MethodGroup group) {

        MethodEntry found = null;
        search: for (MethodEntry mth : next.getMethods()) {
            if (mth.getName().equals(name)) {
                if (mth.getDescription().equals(desc)) {
                    MethodGroup current = this.groups.get(mth);
                    if (current == null) {
                        group.addMethod(mth);
                        this.groups.put(mth, group);
                    } else {
                        mergeGroups(group, current);
                        group.addMethod(mth);
                        this.groups.put(mth, group);
                    }
                    found = mth;
                    break;
                }
                if (mth.getMethodSignature().getParameters().size() != sig.getParameters().size()) {
                    continue;
                }
                for (int i = 0; i < mth.getMethodSignature().getParameters().size(); i++) {
                    TypeSignature param = mth.getMethodSignature().getParameters().get(i);
                    TypeSignature expected = sig.getParameters().get(i);
                    if (!param.equals(expected)) {
                        continue search;
                    }
                }
                MethodGroup current = this.groups.get(mth);
                if (current == null) {
                    group.addMethod(mth);
                    this.groups.put(mth, group);
                } else {
                    mergeGroups(group, current);
                }
                found = mth;
                break;
            }
        }

        if (found != null) {
            for (TypeEntry sub : this.subtypes.get(next)) {
                discoverOverrides(sub, found.getName(), found.getDescription(), createSubtypeSignature(sub, next, found.getMethodSignature()), group);
            }
        } else {
            for (TypeEntry sub : this.subtypes.get(next)) {
                discoverOverrides(sub, name, desc, createSubtypeSignature(sub, next, sig), group);
            }
        }
    }

    private void mergeGroups(MethodGroup a, MethodGroup b) {
        a.getMethods().addAll(b.getMethods());
        for (MethodEntry mth : b.getMethods()) {
            this.groups.put(mth, a);
        }
    }

}
