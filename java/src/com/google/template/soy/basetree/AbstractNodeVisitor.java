/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.basetree;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Base class for {@code AbstractXxxNodeVisitor} classes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @param <N> The type of {@code Node} that the tree being visited should be composed of.
 * @param <R> The return type.
 *
 * @author Kai Huang
 */
public abstract class AbstractNodeVisitor<N extends Node, R> implements NodeVisitor<N, R> {


  /** Cached values of {@code classToMethodMap} for concrete subclasses. */
  // Important: For each entry, the value must actually be a Map<Class<? extends N>, Method>, where
  // N is the node class that is the parameter of the key, i.e. the corresponding key would be a
  // Class<? extends AbstractNodeVisitor<N>>. (This criterion is not expressible by Java generics
  // syntax.)
  private static Map<Class<? extends AbstractNodeVisitor>, Map>
      concreteVisitorClassToCachedMap = Maps.newHashMap();


  /** A list of all the concrete node classes. */
  private final List<Class<? extends N>> nodeClasses;

  /** A list of all the node interfaces in approximate order of specificity. */
  private final List<Class<? extends N>> nodeInterfaces;

  /** Map from specific node class to appropriate visitInternal() method, or null if no method. */
  private Map<Class<? extends N>, Method> classToMethodMap = null;


  /**
   * @param nodeClasses A list of all the concrete node classes.
   * @param nodeInterfaces A list of all the node interfaces in approximate order of specificity.
   */
  protected AbstractNodeVisitor(List<Class<? extends N>> nodeClasses,
                                List<Class<? extends N>> nodeInterfaces) {
    this.nodeClasses = nodeClasses;
    this.nodeInterfaces = nodeInterfaces;
  }



  @Override public R exec(N node) {
    setup();
    visit(node);
    return getResult();
  }


  /**
   * Sets up (or resets) this visitor so that {@code visit()} can be called with correct results.
   *
   * <p> Does nothing by default. Override this method in a NodeVisitor that requires setup code
   * before every run.
   */
  protected void setup() {}


  /**
   * Visits the given node to execute the function defined by this visitor.
   * @param node The node to visit.
   */
  protected void visit(N node) {

    if (classToMethodMap == null) {
      @SuppressWarnings("unchecked")  // generics limitation (see concreteVisitorClassToCachedMap)
      Map<Class<? extends N>, Method> cachedClassToMethodMap =
          (Map<Class<? extends N>, Method>) concreteVisitorClassToCachedMap.get(this.getClass());
      if (cachedClassToMethodMap != null) {
        classToMethodMap = cachedClassToMethodMap;
      } else {
        classToMethodMap = buildClassToMethodMap();
        concreteVisitorClassToCachedMap.put(this.getClass(), classToMethodMap);
      }
    }

    Method method = classToMethodMap.get(node.getClass());
    if (method == null) {
      throw new UnsupportedOperationException(
          "Node visitor " + this.getClass().getSimpleName() + " is not implemented for class " +
          node.getClass().getSimpleName() + ".");
    }

    try {
      method.invoke(this, node);

    } catch (InvocationTargetException ite) {
      Throwables.propagate(ite.getCause());
    } catch (Exception e) {
      Throwables.propagate(e);
    }
  }


  /**
   * Gets the result computed by the previous call to {@code visit}, or null if this visitor does
   * not return a result.
   *
   * <p> Returns null by default. Override this method in a NodeVisitor that returns a result
   * (i.e. when 'R' is not Void).
   *
   * @return The result computed by the previous call to {@code visit}, or null if this visitor
   *     does not return a result.
   */
  protected R getResult() {
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to build the {@code classToMethodMap}. This function will only be called once
   * for each concrete visitor class because we cache the built maps.
   * @return The built map.
   */
  private Map<Class<? extends N>, Method> buildClassToMethodMap() {

    Map<Class<? extends N>, Method> map = Maps.newHashMap();

    OUTER: for (Class<? extends N> nodeClass : nodeClasses) {

      // First look for a method for the specific node class.
      Method specificMethod = getMethodNamedVisitInternal(nodeClass);
      if (specificMethod != null) {
        map.put(nodeClass, specificMethod);
        continue;
      }

      // If no specific method, look for a method for one of the interfaces.
      for (Class<? extends N> nodeInterface : nodeInterfaces) {
        if (nodeInterface.isAssignableFrom(nodeClass)) {
          Method interfaceMethod = getMethodNamedVisitInternal(nodeInterface);
          if (interfaceMethod != null) {
            map.put(nodeClass, interfaceMethod);
            continue OUTER;
          }
        }
      }

      // If no specific method nor interface method, put null in map.
      map.put(nodeClass, null);
    }

    return Collections.unmodifiableMap(map);
  }


  /**
   * Private helper for {@code buildClassToMethodMap} to find the corresponding
   * {@code visitInternal()} method given a node class or interface.
   *
   * @param nodeClassOrInterface The node class or interface.
   * @return The corresponding {@code visitInternal()} method, or null if not implemented.
   */
  private Method getMethodNamedVisitInternal(Class<? extends N> nodeClassOrInterface) {

    for (Class<?> visitorClass = this.getClass();
         visitorClass.getSuperclass() != AbstractNodeVisitor.class;
         visitorClass = visitorClass.getSuperclass()) {
      try {
        Method method = visitorClass.getDeclaredMethod("visitInternal", nodeClassOrInterface);
        method.setAccessible(true);
        return method;
      } catch (NoSuchMethodException e) {
        // Do nothing. Next iteration of the for-loop will try superclass.
      }
    }

    return null;
  }

}
