/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.internal.http.loader;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.annotations.SdkTestInternalApi;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.exception.HttpImplementationException;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.http.async.SdkAsyncHttpService;
import software.amazon.awssdk.utils.SystemSetting;

/**
 * {@link SdkHttpServiceProvider} implementation that uses {@link ServiceLoader} to find HTTP implementations on the
 * classpath. If more than one implementation is found on the classpath then an exception is thrown.
 */
@SdkInternalApi
final class ClasspathSdkHttpServiceProvider<T> implements SdkHttpServiceProvider<T> {

    private final SdkServiceLoader serviceLoader;
    private final SystemSetting implSystemProperty;
    private final Class<T> serviceClass;

    @SdkTestInternalApi
    ClasspathSdkHttpServiceProvider(SdkServiceLoader serviceLoader, SystemSetting implSystemProperty, Class<T> serviceClass) {
        this.serviceLoader = serviceLoader;
        this.implSystemProperty = implSystemProperty;
        this.serviceClass = serviceClass;
    }

    @Override
    public Optional<T> loadService() {
        Iterable<T> iterable = () -> serviceLoader.loadServices(serviceClass);
        List<T> impls = StreamSupport
            .stream(iterable.spliterator(), false)
            .collect(Collectors.toList());

        if (impls.isEmpty()) {
            return Optional.empty();
        }

        if (impls.size() > 1) {

            String implText =
                impls.stream()
                     .map(clazz -> clazz.getClass().getName())
                     .collect(Collectors.joining(",", "[", "]"));

            throw HttpImplementationException.builder().message(
                    String.format(
                            "Multiple HTTP implementations were found on the classpath. To avoid non-deterministic loading " +
                            "implementations, please explicitly provide an HTTP client via the client builders, set the %s " +
                            "system property with the FQCN of the HTTP service to use as the default, or remove all but one " +
                            "HTTP implementation from the classpath.  The multiple implementations found were: %s",
                            implSystemProperty.property(), implText))
                    .build();
        }

        return impls.stream().findFirst();
    }

    /**
     * @return ClasspathSdkHttpServiceProvider that loads an {@link SdkHttpService} (sync) from the classpath.
     */
    static SdkHttpServiceProvider<SdkHttpService> syncProvider() {
        return new ClasspathSdkHttpServiceProvider<>(SdkServiceLoader.INSTANCE,
                                                     SdkSystemSetting.SYNC_HTTP_SERVICE_IMPL,
                                                     SdkHttpService.class);
    }

    /**
     * @return ClasspathSdkHttpServiceProvider that loads an {@link SdkAsyncHttpService} (async) from the classpath.
     */
    static SdkHttpServiceProvider<SdkAsyncHttpService> asyncProvider() {
        return new ClasspathSdkHttpServiceProvider<>(SdkServiceLoader.INSTANCE,
                                                     SdkSystemSetting.ASYNC_HTTP_SERVICE_IMPL,
                                                     SdkAsyncHttpService.class);
    }

}
