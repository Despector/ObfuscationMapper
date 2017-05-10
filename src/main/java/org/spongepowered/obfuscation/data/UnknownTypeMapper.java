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
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.despector.ast.type.TypeVisitor;
import org.spongepowered.obfuscation.merge.MergeEngine;

public class UnknownTypeMapper implements TypeVisitor {

    private final MappingsSet mappings;
    private final UnknownPackageDiscovery packages;

    private int next_type = 0;

    public UnknownTypeMapper(MergeEngine engine, MappingsSet set) {
        this.mappings = set;
        this.packages = new UnknownPackageDiscovery(engine);
        this.packages.build();
    }

    private String mapName(TypeEntry typeentry) {
        String type = typeentry.getName();
        String mapped = this.mappings.mapType(type);
        String mapped_child = null;
        if (mapped != null && type.lastIndexOf('$') != -1) {
            mapped_child = mapped.substring(mapped.lastIndexOf('$') + 1, mapped.length());
            mapped = null;
        }
        if (mapped == null) {
            if (type.lastIndexOf('$') != -1) {
                String parent = type.substring(0, type.lastIndexOf('$'));
                TypeEntry parent_type = typeentry.getSource().get(parent);
                String child = type.substring(type.lastIndexOf('$') + 1, type.length());
                String mapped_parent = mapName(parent_type);
                if (mapped_child == null) {
                    try {
                        int index = Integer.valueOf(child);
                        mapped = mapped_parent + "$" + index;
                        while (this.mappings.inverseType(mapped) != null) {
                            index++;
                            mapped = mapped_parent + "$" + index;
                        }
                    } catch (NumberFormatException e) {
                        child = String.format("CL_%04d_%s", this.next_type++, child);
                        mapped = mapped_parent + "$" + child;
                    }
                } else {
                    mapped = mapped_parent + "$" + mapped_child;
                }
                this.mappings.addTypeMapping(type, mapped);
                return mapped;
            }
            String pkg = this.packages.getPackage(typeentry);
            mapped = String.format("%s/CL_%04d_%s", pkg, this.next_type++, type);
            this.mappings.addTypeMapping(type, mapped);
        }
        return mapped;
    }

    @Override
    public void visitClassEntry(ClassEntry type) {
        mapName(type);
    }

    @Override
    public void visitEnumEntry(EnumEntry type) {
        mapName(type);
    }

    @Override
    public void visitInterfaceEntry(InterfaceEntry type) {
        mapName(type);
    }

    @Override
    public void visitAnnotationEntry(AnnotationEntry type) {
        mapName(type);
    }

    @Override
    public void visitAnnotation(Annotation annotation) {
    }

    @Override
    public void visitMethod(MethodEntry mth) {
    }

    @Override
    public void visitMethodEnd() {
    }

    @Override
    public void visitField(FieldEntry fld) {
    }

    @Override
    public void visitTypeEnd() {
    }

}
