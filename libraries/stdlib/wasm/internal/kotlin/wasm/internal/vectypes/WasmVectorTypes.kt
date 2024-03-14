/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:ExcludedFromCodegen

package kotlin.wasm.internal.vectypes

import kotlin.wasm.internal.*

@Suppress("UNUSED_PARAMETER")
@WasmOp(WasmOp.V128_CONST)
@WasmIntrinsicArguments
public fun v128OfUBytes(
    b0: UByte, b1: UByte, b2: UByte, b3: UByte,
    b4: UByte, b5: UByte, b6: UByte, b7: UByte,
    b8: UByte, b9: UByte, b10: UByte, b11: UByte,
    b12: UByte, b13: UByte, b14: UByte, b15: UByte
): Vec128 = implementedAsIntrinsic

public class Vec128 @WasmPrimitiveConstructor private constructor() {
    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun plus(other: Vec128): Vec128 = wasm_i8x16_add(this, other)
}