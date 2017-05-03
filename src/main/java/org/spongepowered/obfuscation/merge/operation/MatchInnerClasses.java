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
import org.spongepowered.despector.ast.type.EnumEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MatchInnerClasses implements MergeOperation {

    private boolean prepared = false;

    private final Multimap<TypeEntry, TypeEntry> old_inners = HashMultimap.create();
    private final Multimap<TypeEntry, TypeEntry> new_inners = HashMultimap.create();

    private void prep(MergeEngine set) {
        for (TypeEntry type : set.getOldSourceSet().getAllClasses()) {
            if (!type.getName().contains("$")) {
                continue;
            }
            String parent_name = type.getName().substring(0, type.getName().lastIndexOf('$'));
            TypeEntry parent = set.getOldSourceSet().get(parent_name);
            if (parent == null) {
                throw new IllegalStateException(parent_name + " not found as parent type");
            }
            this.old_inners.put(parent, type);
        }
        for (TypeEntry type : set.getNewSourceSet().getAllClasses()) {
            if (!type.getName().contains("$")) {
                continue;
            }
            String parent_name = type.getName().substring(0, type.getName().lastIndexOf('$'));
            TypeEntry parent = set.getNewSourceSet().get(parent_name);
            if (parent == null) {
                throw new IllegalStateException(parent_name + " not found as parent type");
            }
            this.new_inners.put(parent, type);
        }
    }

    @Override
    public void operate(MergeEngine set) {
        if (!this.prepared) {
            this.prepared = true;
            prep(set);
        }

        for (TypeEntry type : set.getOldSourceSet().getAllClasses()) {
            MatchEntry match = set.getMatch(type);
            if (match == null) {
                continue;
            }
            if (type.getName().contains("$")) {
                String parent_name = type.getName().substring(0, type.getName().lastIndexOf('$'));
                TypeEntry parent = set.getOldSourceSet().get(parent_name);
                MatchEntry parent_match = set.getPendingMatch(parent);
                if (parent != null && parent_match.getNewType() == null) {
                    String new_parent_name = match.getNewType().getName().substring(0, match.getNewType().getName().lastIndexOf('$'));
                    TypeEntry new_parent = set.getNewSourceSet().get(new_parent_name);
                    if (new_parent != null) {
                        set.vote(parent, new_parent);
                    }
                }
            }
            TypeEntry new_parent = match.getNewType();
            Collection<TypeEntry> old_all_inners = this.old_inners.get(type);
            if (old_all_inners == null) {
                continue;
            }
            Collection<TypeEntry> new_all_inners = this.new_inners.get(new_parent);
            if (new_all_inners == null) {
                continue;
            }
            mergeDistinct(set, old_all_inners, new_all_inners);
        }

    }

    private void mergeDistinct(MergeEngine set, Collection<TypeEntry> old_inners, Collection<TypeEntry> new_inners) {
        List<TypeEntry> old_anon = new ArrayList<>();
        List<TypeEntry> new_anon = new ArrayList<>();
        List<TypeEntry> old_named = new ArrayList<>();
        List<TypeEntry> new_named = new ArrayList<>();

        for (TypeEntry type : old_inners) {
            if (type.isAnonType()) {
                old_anon.add(type);
            } else {
                old_named.add(type);
            }
        }

        for (TypeEntry type : new_inners) {
            if (type.isAnonType()) {
                new_anon.add(type);
            } else {
                new_named.add(type);
            }
        }

        if (old_anon.size() == 1 && new_anon.size() == 1) {
            set.vote(old_anon.get(0), new_anon.get(0));
        } else {
        }

        if (old_named.size() == 1 && new_named.size() == 1) {
            set.vote(old_named.get(0), new_named.get(0));
        } else {
            for (TypeEntry type : old_named) {
                if (set.isTypeMatched(type)) {
                    continue;
                }
                if (type instanceof EnumEntry) {
                    TypeEntry possible = null;
                    int matches = 0;

                    for (TypeEntry pos : new_named) {
                        if (!(pos instanceof EnumEntry)) {
                            continue;
                        }
                        EnumEntry pos_enum = (EnumEntry) pos;
                        int m = 0;
                        for (String cst : ((EnumEntry) type).getEnumConstants()) {
                            if (pos_enum.getEnumConstants().contains(cst)) {
                                m++;
                            }
                        }
                        if (m > matches) {
                            possible = pos;
                        } else if (m == matches) {
                            possible = null;
                        }
                    }
                    if (possible != null) {
                        set.vote(type, possible);
                    }
                    continue;
                }
            }
        }
    }

}
