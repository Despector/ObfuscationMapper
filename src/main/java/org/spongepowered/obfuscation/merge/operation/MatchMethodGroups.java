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

import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodGroup;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.HashSet;
import java.util.Set;

public class MatchMethodGroups implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {

        Set<MethodGroup> handled = new HashSet<>();

        for (MethodMatchEntry match : set.getAllMethodMatches()) {
            MethodGroup old_group = set.getOldMethodGroup(match.getOldMethod());
            if (old_group.getMethods().size() == 1) {
                continue;
            }
            if (handled.contains(old_group)) {
                continue;
            }
            handled.add(old_group);
            MethodGroup new_group = set.getNewMethodGroup(match.getNewMethod());

            for (MethodEntry old : old_group.getMethods()) {
                MethodMatchEntry m = set.getMethodMatch(old);
                if (m != null) {
                    continue;
                }
                TypeEntry old_owner = set.getOldSourceSet().get(old.getOwnerName());
                MatchEntry type_match = set.getMatch(old_owner);
                if (type_match == null) {
                    continue;
                }
                MethodEntry n = new_group.getOverride(type_match.getNewType());
                if (n != null) {
                    set.vote(old, n);
                }
            }
        }
    }

}
