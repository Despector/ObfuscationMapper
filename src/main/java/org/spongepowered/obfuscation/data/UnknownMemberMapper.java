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

public class UnknownMemberMapper implements TypeVisitor {

    private final MappingsSet mappings;

    private int next_type = 0;

    public UnknownMemberMapper(MappingsSet set) {
        this.mappings = set;
    }

    @Override
    public void visitClassEntry(ClassEntry type) {
    }

    @Override
    public void visitEnumEntry(EnumEntry type) {
    }

    @Override
    public void visitInterfaceEntry(InterfaceEntry type) {
    }

    @Override
    public void visitAnnotationEntry(AnnotationEntry type) {
    }

    @Override
    public void visitAnnotation(Annotation annotation) {
    }

    @Override
    public void visitMethod(MethodEntry mth) {
        if (mth.getName().equals("<init>") || mth.getName().equals("<clinit>")) {
            return;
        }
        String mapped = this.mappings.mapMethod(mth.getOwnerName(), mth.getName(), mth.getDescription());
        if (mapped == null) {
            mapped = String.format("mth_%04d_%s", this.next_type++, mth.getName());
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
            mapped = String.format("fld_%04d_%s", this.next_type++, fld.getName());
            this.mappings.addFieldMapping(fld.getOwnerName(), fld.getName(), mapped);
        }
    }

    @Override
    public void visitTypeEnd() {
    }

}
