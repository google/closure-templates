/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.javasrc.dyncompile;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;


/**
 * Inspects a {@link ClassLoader} to enumerate the classes available.
 *
 */
final class ClasspathUtils {

  /** The ClassLoader used to resolve package names. */
  private static final ClassLoader CLASS_LOADER = ClasspathUtils.class.getClassLoader() == null
      ? ClassLoader.getSystemClassLoader() : ClasspathUtils.class.getClassLoader();


  /**
   * The resource paths for each class loadable by the Classloader in the given
   * package (and subpackages iff {@code isRecursive}).
   * E.g. the resource path for a class {@code java.lang.Class} is {@code java/lang/Class.class}.
   * Resource paths are URL paths, so the file separator {@code /} is used regardless of the
   * underlying file system.
   *
   * @param isRecursive True iff class enumeration should recurse into sub-packages.
   * @return The resource path for each loadable class in no particular order.
   */
  static Iterable<String> getClassResourcePaths(String packageName, boolean isRecursive)
      throws IOException {
    ImmutableList.Builder<String> classResourcePaths = ImmutableList.builder();
    String relativeUrl = packageName.replace('.', '/');

    PackageContent packageContent = getPackagePathToContentMap().get(relativeUrl);
    if (packageContent != null) {
      packageContent.enumerateResources(classResourcePaths, isRecursive);
    }

    // The ClassLoader might layer class resolution on top of the URL class loader, so look up
    // using the ClassLoader built-in too.
    @Nullable URL url = CLASS_LOADER.getResource(relativeUrl);
    if (url != null) {  // Assumes the package is entirely contained in one JAR or root directory.
      String protocol = url.getProtocol();
      if ("file".equals(protocol)) {
        try {
          searchFileTree(packageName, new File(url.toURI()), isRecursive, classResourcePaths);
        } catch (URISyntaxException ex) {
          Throwables.propagate(ex);
        }
      } else if ("jar".equals(protocol)) {
        searchZipFile(packageName, isRecursive, url, classResourcePaths);
      }
    }
    return classResourcePaths.build();
  }


  /**
   * Walks a file tree looking for class files.
   *
   * @param packageName The package name corresponding to f.  It is the empty string if f is the
   *     root of a package tree.
   * @param f The sub-tree to walk.
   * @param isRecursive True to descend into directories under f.
   * @param classResourcePaths Receives class resource paths.
   */
  private static void searchFileTree(
      String packageName, File f, boolean isRecursive,
      ImmutableList.Builder<? super String> classResourcePaths) {
    if (f.isDirectory()) {
      for (File child : f.listFiles()) {
        if (child.isFile()) {
          if (child.getName().endsWith(".class")) {
            String packagePath = packageName.equals("") ?
                "" : packageName.replace('.', '/') + "/";
            classResourcePaths.add(packagePath + child.getName());
          }
        } else if (isRecursive && child.isDirectory()) {
          if (!(".".equals(child.getName()) || "..".equals(child.getName()))) {
            String childPackageName = child.getName();
            if (!"".equals(packageName)) {
              childPackageName = packageName + "." + childPackageName;
            }
            searchFileTree(childPackageName, child, isRecursive, classResourcePaths);
          }
        }
      }
    }
  }


  /**
   * Inspect the table of contents of a ZIP file (JARs are a kind of ZIP file) to find class
   * files.
   *
   * @param packageName The package name corresponding to f.  It is the empty string if f is the
   *     root of a package tree.
   * @param url URL to a ZIP file.
   * @param isRecursive True to descend into directories under f.
   * @param classResourcePaths Receives class resource paths.
   */
  private static void searchZipFile(
      String packageName, boolean isRecursive, URL url,
      ImmutableList.Builder<? super String> classResourcePaths)
      throws IOException {
    // Expect a non-hierarchical of the form "jar:" (URL to jar file) "!" (path within jar)
    String urlString = url.toString();  // Not a hierarchical URL.
    int bangIndex = urlString.lastIndexOf('!');
    URL zipFileUrl = new URL(urlString.substring(4, bangIndex));
    String pathInJar = urlString.substring(bangIndex + 1);
    searchZipAtFileForPath(pathInJar, isRecursive, zipFileUrl, classResourcePaths);
  }


  /**
   * Iterates through the entries in a ZIP file to enumerate all the resources under a particular
   * package tree.
   *
   * @param pathInJar A package path like {@code "java/lang"}.
   * @param isRecursive True iff sub-packages should be included.
   * @param zipFileUrl A gettable URL to a ZIP file.
   * @param classResourcePaths Receives resource paths like {@code "java/lang/Object.class"}.
   */
  private static void searchZipAtFileForPath(
      String pathInJar, boolean isRecursive, URL zipFileUrl,
      ImmutableList.Builder<? super String> classResourcePaths)
      throws IOException {
    InputStream in = zipFileUrl.openStream();
    try {
      ZipInputStream zipIn = new ZipInputStream(in);
      String prefix = pathInJar;
      if (pathInJar.startsWith("/")) {
        prefix = prefix.substring(1);
      }
      for (ZipEntry zipEntry; (zipEntry = zipIn.getNextEntry()) != null;) {
        String zipEntryName = zipEntry.getName();
        if (!zipEntry.isDirectory() && zipEntryName.endsWith(".class") &&
            zipEntryName.startsWith(prefix)) {
          if (zipEntryName.lastIndexOf('/') == prefix.length() ||  // Not in a subdirectory.
              (isRecursive &&
               (prefix.length() == 0 || zipEntryName.charAt(prefix.length()) == '/'))) {
            classResourcePaths.add(zipEntryName);
          }
        }
      }
    } finally {
      in.close();
    }
  }


  /** A soft reference to a package structure that can be GCed once Soy compiling is done. */
  private static SoftReference<Map<String, PackageContent>> packagePathToContentMapRef;


  /**
   * Returns a mapping from package paths (e.g. {@code "java/lang"}) to the contents of that
   * package that are findable from the {@code URLClassLoader}s.
   */
  private static Map<String, PackageContent> getPackagePathToContentMap() {
    Map<String, PackageContent> packagePathToContentMap = null;
    if (packagePathToContentMapRef != null) {
      packagePathToContentMap = packagePathToContentMapRef.get();
    }
    if (packagePathToContentMap == null) {
      packagePathToContentMap = buildPackagePathToContentMapFromURLClassLoaders(CLASS_LOADER);
      packagePathToContentMapRef = new SoftReference<Map<String, PackageContent>>(
          packagePathToContentMap);
    }
    return packagePathToContentMap;
  }


  /**
   * A helper for {@link #getPackagePathToContentMap} that builds a tree by looking at the URLs in
   * the URL class loader.
   * This works around problems with packages that are split amongst multiple jars which are
   * not handled properly by the ClassLoader.getResource(...) scheme since that can return at
   * most one URL.
   */
  private static Map<String, PackageContent> buildPackagePathToContentMapFromURLClassLoaders(
      ClassLoader classLoader) {
    ImmutableList.Builder<String> classResourcePaths = ImmutableList.builder();
    for (ClassLoader cl = classLoader; cl != null; cl = cl.getParent()) {
      if (!(cl instanceof URLClassLoader)) {
        continue;
      }
      for (URL classpathRoot : ((URLClassLoader) cl).getURLs()) {
        String protocol = classpathRoot.getProtocol();
        if (!"file".equals(protocol)) {
          continue;
        }
        try {
          File f = new File(classpathRoot.toURI());
          if (!f.exists()) {
            continue;
          }
          try {
            if (f.isDirectory()) {
              searchFileTree("", f, true, classResourcePaths);
            } else if (f.isFile()) {
              searchZipAtFileForPath("", true, classpathRoot, classResourcePaths);
            }
          } catch (IOException ex) {
            // Treat unreadable ZIP files and directories as if they didn't exist.
          }
        } catch (URISyntaxException ex) {
          Throwables.propagate(ex);
        }
      }
      break;
    }
    PackageTree packageTree = new PackageTree();
    for (String classResourcePath : classResourcePaths.build()) {
      int last = classResourcePath.lastIndexOf('/');
      String packagePart = last >= 0 ? classResourcePath.substring(0, last) : "";
      packageTree.getPackageContent(packagePart).resources.add(classResourcePath);
    }
    return ImmutableMap.copyOf(packageTree.packagePathToContentMap);
  }


  /**
   * A helper class for {@link #getPackagePathToContentMap} that maps from package paths to lists
   * of resources in those packages.
   */
  private static final class PackageTree {
    /** Keys on package paths like "com/example/foo". */
    final Map<String, PackageContent> packagePathToContentMap = Maps.newHashMap();

    PackageContent getPackageContent(String packagePath) {
      PackageContent packageContent = packagePathToContentMap.get(packagePath);
      if (packageContent == null) {
        packageContent = new PackageContent();
        packagePathToContentMap.put(packagePath, packageContent);
        if (!"".equals(packagePath)) {
          int lastSlash = packagePath.lastIndexOf('/');
          String parentPackagePath = lastSlash < 0 ? "" : packagePath.substring(0, lastSlash);
          getPackageContent(parentPackagePath).subPackages.add(packageContent);
        }
      }
      return packageContent;
    }
  }


  /**
   * Bundles the relative paths to classes in a specific package and a list of sub-packages.
   */
  private static final class PackageContent {
    final List<PackageContent> subPackages = Lists.newArrayList();
    final List<String> resources = Lists.newArrayList();

    void enumerateResources(ImmutableList.Builder<String> out, boolean recursive) {
      out.addAll(resources);
      if (recursive) {
        for (PackageContent subPackage : subPackages) {
          subPackage.enumerateResources(out, true);
        }
      }
    }
  }

}
