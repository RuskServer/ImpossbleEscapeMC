package com.lunar_prototype.impossbleEscapeMC.core.di.annotations;

import java.lang.annotation.*;

/** DIの自動検出で使うマーカー。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Repository {}
