/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.driver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Queue;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.driver.MacroOption.AddedTwiceException;
import com.oracle.svm.driver.MacroOption.InvalidMacroException;
import com.oracle.svm.driver.MacroOption.VerboseInvalidMacroException;

class MacroOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    MacroOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    public boolean consume(Queue<String> args) {
        String headArg = args.peek();
        boolean consumed = false;
        try {
            consumed = nativeImage.optionRegistry.enableOption(headArg, new HashSet<>(), null, this::applyEnabled);
        } catch (VerboseInvalidMacroException e1) {
            NativeImage.showError(e1.getMessage(nativeImage.optionRegistry));
        } catch (InvalidMacroException | AddedTwiceException e) {
            NativeImage.showError(e.getMessage());
        } catch (NativeImage.NativeImageError err) {
            NativeImage.showError("Applying MacroOption " + headArg + " failed", err);
        }
        if (consumed) {
            args.poll();
        }
        return consumed;
    }

    private static final String PATH_SEPARATOR_REGEX;
    static {
        if (OS.getCurrent().equals(OS.WINDOWS)) {
            PATH_SEPARATOR_REGEX = ":|;";
        } else {
            PATH_SEPARATOR_REGEX = File.pathSeparator;
        }
    }

    private void applyEnabled(MacroOption.EnabledOption enabledOption) {
        Path imageJarsDirectory = enabledOption.getOption().getOptionDirectory();
        if (imageJarsDirectory == null) {
            return;
        }

        if (!nativeImage.config.useJavaModules()) {
            enabledOption.forEachPropertyValue("ImageBuilderBootClasspath8", entry -> nativeImage.addImageBuilderBootClasspath(ClasspathUtils.stringToClasspath(entry)), PATH_SEPARATOR_REGEX);
        }

        if (!enabledOption.forEachPropertyValue("ImageBuilderClasspath", entry -> nativeImage.addImageBuilderClasspath(ClasspathUtils.stringToClasspath(entry)), PATH_SEPARATOR_REGEX)) {
            Path builderJarsDirectory = imageJarsDirectory.resolve("builder");
            if (Files.isDirectory(builderJarsDirectory)) {
                NativeImage.getJars(builderJarsDirectory).forEach(nativeImage::addImageBuilderClasspath);
            }
        }

        if (!enabledOption.forEachPropertyValue("ImageClasspath", entry -> nativeImage.addImageClasspath(ClasspathUtils.stringToClasspath(entry)), PATH_SEPARATOR_REGEX)) {
            NativeImage.getJars(imageJarsDirectory).forEach(nativeImage::addImageClasspath);
        }

        String imageName = enabledOption.getProperty("ImageName");
        if (imageName != null) {
            nativeImage.addPlainImageBuilderArg(nativeImage.oHName + imageName);
        }

        String imagePath = enabledOption.getProperty("ImagePath");
        if (imagePath != null) {
            nativeImage.addPlainImageBuilderArg(nativeImage.oHPath + imagePath);
        }

        String imageClass = enabledOption.getProperty("ImageClass");
        if (imageClass != null) {
            nativeImage.addPlainImageBuilderArg(nativeImage.oHClass + imageClass);
        }

        enabledOption.forEachPropertyValue("JavaArgs", nativeImage::addImageBuilderJavaArgs);
        NativeImage.NativeImageArgsProcessor args = nativeImage.new NativeImageArgsProcessor();
        enabledOption.forEachPropertyValue("Args", args);
        args.apply(true);
    }
}
