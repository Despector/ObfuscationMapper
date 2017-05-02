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
package org.spongepowered.obfuscation.merge.data;

import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MethodGroup {

    private final MethodEntry root;
    private final Set<MethodEntry> methods = new HashSet<>();

    public MethodGroup(MethodEntry root) {
        this.root = root;
        this.methods.add(root);
    }

    public MethodEntry getArchetype() {
        return this.root;
    }

    public String getName() {
        return this.root.getName();
    }

    public String getDescription() {
        return this.root.getDescription();
    }

    public boolean isStatic() {
        return this.root.isStatic();
    }

    public boolean isSynthetic() {
        return this.root.isSynthetic();
    }

    public void addMethod(MethodEntry entry) {
        this.methods.add(entry);
    }

    public Collection<MethodEntry> getMethods() {
        return this.methods;
    }

    public MethodEntry getOverride(TypeEntry type) {
        for (MethodEntry mth : this.methods) {
            if (mth.getOwnerName().equals(type.getName())) {
                return mth;
            }
        }
        return null;
    }

}
