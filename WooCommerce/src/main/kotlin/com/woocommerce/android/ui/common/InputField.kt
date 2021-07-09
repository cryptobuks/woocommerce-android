package com.woocommerce.android.ui.common

import android.os.Parcelable
import com.woocommerce.android.R.string
import com.woocommerce.android.model.UiString
import com.woocommerce.android.model.UiString.UiStringRes
import kotlinx.parcelize.Parcelize

abstract class InputField<T : InputField<T>>(open val content: String) : Parcelable, Cloneable {
    var error: UiString? = null
        private set
    private var hasBeenValidated: Boolean = false
    val isValid: Boolean
        get() {
            return if (!hasBeenValidated) validateInternal() == null
            else error == null
        }

    fun validate(): T {
        val clone = this.clone() as T
        clone.error = validateInternal()
        clone.hasBeenValidated = true
        return clone
    }

    final override fun hashCode(): Int {
        return content.hashCode() + error.hashCode() + hasBeenValidated.hashCode()
    }

    final override fun equals(other: Any?): Boolean {
        if (other !is InputField<*>) return false
        return content == other.content &&
            error == other.error &&
            hasBeenValidated == other.hasBeenValidated
    }

    /**
     * Perform specific field's validation
     * @return [UiString] holding the error to be displayed or null if it's valid
     */
    protected abstract fun validateInternal(): UiString?
}

@Parcelize
data class RequiredField(
    override val content: String
) : InputField<RequiredField>(content) {
    override fun validateInternal(): UiString? {
        return if (content.isBlank()) UiStringRes(string.shipping_label_error_required_field)
        else null
    }
}

@Parcelize
data class OptionalField(
    override val content: String
) : InputField<OptionalField>(content) {
    override fun validateInternal(): UiString? = null
}
