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

import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;

public class MergeMatchedTypes implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {

        for (MatchEntry match : set.getAllMatches()) {
            if (match.isMerged()) {
                continue;
            }
            match.setAsMerged();
            TypeEntry old = match.getOldType();
            TypeEntry new_ = match.getNewType();

            if (old instanceof ClassEntry) {
                TypeEntry old_super = set.getOldSourceSet().get(((ClassEntry) old).getSuperclassName());
                TypeEntry new_super = set.getNewSourceSet().get(((ClassEntry) new_).getSuperclassName());
                if (old_super != null && new_super != null) {
                    set.vote(old_super, new_super);
                }
            }
            
            
            
        }

    }

}
