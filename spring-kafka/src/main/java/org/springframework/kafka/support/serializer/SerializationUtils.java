/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.kafka.support.serializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

import org.apache.kafka.common.header.Headers;

import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Utilities for serialization.
 *
 * @author Gary Russell
 * @since 2.5
 *
 */
public final class SerializationUtils {

	private SerializationUtils() {
	}

	/**
	 * Convert a property value (FQCN.methodName) to a {@link BiFunction} that takes a
	 * payload and headers and returns some value. The method must have parameters
	 * {@code (P, Headers)} or {@code (P)} and be declared as static.
	 * @param <P> The {@link BiFunction} first parameter type.
	 * @param <T> The {@link BiFunction} return type.
	 * @param methodProperty the method name property.
	 * @param payloadType the {@link BiFunction} first parameter type.
	 * @param classLoader the class loader.
	 * @return the function.
	 */
	@SuppressWarnings("unchecked")
	public static <P, T> BiFunction<P, Headers, T> propertyToMethodInvokingFunction(String methodProperty,
			Class<P> payloadType, ClassLoader classLoader) {

		int lastDotPosn = methodProperty.lastIndexOf('.');
		Assert.state(lastDotPosn > 1,
				"the method property needs to be a class name followed by the method name, separated by '.'");
		BiFunction<P, Headers, T> function;
		Class<?> clazz;
		try {
			clazz = ClassUtils.forName(methodProperty.substring(0, lastDotPosn), classLoader);
		}
		catch (ClassNotFoundException | LinkageError e) {
			throw new IllegalStateException(e);
		}
		String methodName = methodProperty.substring(lastDotPosn + 1);
		Method method;
		try {
			method = clazz.getDeclaredMethod(methodName, payloadType, Headers.class);
		}
		catch (@SuppressWarnings("unused") NoSuchMethodException e) {
			try {
				method = clazz.getDeclaredMethod(methodName, payloadType);
			}
			catch (@SuppressWarnings("unused") NoSuchMethodException e1) {
				IllegalStateException ise =
						new IllegalStateException("the parser method must take '("
								+ payloadType.getSimpleName()
								+ ", Headers)' or '("
								+ payloadType.getSimpleName()
								+ ")'", e1);
				ise.addSuppressed(e);
				throw ise;
			}
			catch (SecurityException e1) {
				IllegalStateException ise = new IllegalStateException(e1);
				ise.addSuppressed(e);
				throw ise;
			}
		}
		catch (SecurityException e) {
			throw new IllegalStateException(e);
		}
		Method parseMethod = method;
		if (method.getParameters().length > 1) {
			function = (str, headers) -> {
				try {
					return (T) parseMethod.invoke(null, str, headers);
				}
				catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new IllegalStateException(e);
				}
			};
		}
		else {
			function = (str, headers) -> {
				try {
					return (T) parseMethod.invoke(null, str);
				}
				catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new IllegalStateException(e);
				}
			};
		}
		return function;
	}

}
