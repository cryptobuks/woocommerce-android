package com.woocommerce.android.ui.compose.component

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.woocommerce.android.R
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground

/**
 * An [OutlinedTextField] that displays an optional helper text below the field.
 */
@Composable
fun WCOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(),
    placeholderText: String? = null
) {
    WCOutlinedTextFieldLayout(
        modifier = modifier,
        helperText = helperText,
        isError = isError
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(text = label)
            },
            enabled = enabled,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            colors = colors,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            interactionSource = interactionSource,
            placeholder = {
                placeholderText?.let {
                    Text(text = it)
                }
            }
        )
    }
}

/**
 * An [OutlinedTextField] that displays an optional helper text below the field.
 */
@Composable
fun WCOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(),
    placeholderText: String? = null
) {
    WCOutlinedTextFieldLayout(
        modifier = modifier,
        helperText = helperText,
        isError = isError
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(text = label)
            },
            enabled = enabled,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            colors = colors,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            interactionSource = interactionSource,
            placeholder = {
                placeholderText?.let {
                    Text(text = it)
                }
            }
        )
    }
}

@Composable
private fun WCOutlinedTextFieldLayout(
    helperText: String? = null,
    isError: Boolean = false,
    modifier: Modifier,
    textField: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        textField()
        if (!helperText.isNullOrEmpty()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.caption,
                color = if (!isError) colorResource(id = R.color.color_on_surface_medium)
                else MaterialTheme.colors.error,
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.major_100))
            )
        }
    }
}

@Preview(name = "Light mode")
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WCOutlinedTextFieldPreview() {
    WooThemeWithBackground {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WCOutlinedTextField(value = "", label = "Label", onValueChange = {})
            WCOutlinedTextField(value = "Value", label = "Label", onValueChange = {})
            WCOutlinedTextField(value = "Value", label = "Label", onValueChange = {}, enabled = false)
            WCOutlinedTextField(value = "Value", label = "Label", onValueChange = {}, helperText = "Helper Text")
            WCOutlinedTextField(
                value = "Value",
                label = "Label",
                onValueChange = {},
                helperText = "Helper Text",
                isError = true
            )
        }
    }
}
