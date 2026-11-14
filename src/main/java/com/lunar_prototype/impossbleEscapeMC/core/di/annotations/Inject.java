package com.lunar_prototype.impossbleEscapeMC.core.di.annotations;

import java.lang.annotation.*;

/** コンストラクタ依存を明示する。 */
@Target(ElementType.CONSTRUCTOR)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {}
