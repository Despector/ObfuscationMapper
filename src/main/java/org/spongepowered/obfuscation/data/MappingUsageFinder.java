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
import org.spongepowered.despector.ast.type.AnnotationEntry;
import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.EnumEntry;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.InterfaceEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeVisitor;

import java.util.HashSet;
import java.util.Set;

public class MappingUsageFinder implements TypeVisitor {

    private final MappingsSet set;
    private Set<String> types = new HashSet<>();
    private Set<String> methods = new HashSet<>();
    private Set<String> fields = new HashSet<>();

    public MappingUsageFinder(MappingsSet set) {
        this.set = set;
    }

    public int getSeenTypes() {
        return this.types.size();
    }

    public boolean sawType(String obf) {
        return this.types.contains(obf);
    }

    public int getSeenMethods() {
        return this.methods.size();
    }

    public int getSeenFields() {
        return this.fields.size();
    }

    @Override
    public void visitClassEntry(ClassEntry type) {
        if (this.set.mapType(type.getName()) != null) {
            this.types.add(type.getName());
        }
    }

    @Override
    public void visitEnumEntry(EnumEntry type) {
        if (this.set.mapType(type.getName()) != null) {
            this.types.add(type.getName());
        }
    }

    @Override
    public void visitInterfaceEntry(InterfaceEntry type) {
        if (this.set.mapType(type.getName()) != null) {
            this.types.add(type.getName());
        }
    }

    @Override
    public void visitAnnotationEntry(AnnotationEntry type) {
        if (this.set.mapType(type.getName()) != null) {
            this.types.add(type.getName());
        }
    }

    @Override
    public void visitAnnotation(Annotation annotation) {
    }

    @Override
    public void visitMethod(MethodEntry mth) {
        if (this.set.mapMethod(mth.getOwnerName(), mth.getName(), mth.getDescription()) != null) {
            this.methods.add(mth.getOwnerName() + mth.getName() + mth.getDescription());
        }
    }

    @Override
    public void visitMethodEnd() {
    }

    @Override
    public void visitField(FieldEntry fld) {
        if (this.set.mapField(fld.getOwnerName(), fld.getName()) != null) {
            this.fields.add(fld.getOwnerName() + fld.getName());
        }
    }

    @Override
    public void visitTypeEnd() {
    }

}
