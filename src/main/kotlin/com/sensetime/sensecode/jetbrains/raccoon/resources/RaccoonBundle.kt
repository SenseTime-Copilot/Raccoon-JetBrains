package com.sensetime.sensecode.jetbrains.raccoon.resources

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey


@NonNls
private const val BUNDLE = "messages.RaccoonBundle"

object RaccoonBundle {
    private val bundle = DynamicBundle(RaccoonBundle::class.java, BUNDLE)

    @Nls
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)
}
//package com.sensetime.sensecode.jetbrains.raccoon.resources
//
//import org.jetbrains.annotations.Nls
//import org.jetbrains.annotations.NonNls
//import org.jetbrains.annotations.PropertyKey
//import java.util.Locale
//import java.util.ResourceBundle
//
//@NonNls
//private const val BUNDLE = "messages.RaccoonBundle"
//object RaccoonBundle {
//    private var currentLocale: Locale = Locale.getDefault()
//    private var resourceBundle: ResourceBundle = ResourceBundle.getBundle(BUNDLE, currentLocale)
//
//    @Nls
//    @JvmStatic
//    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
//        val pattern = resourceBundle.getString(key)
//        return String.format(currentLocale, pattern, *params)
//    }
//
//    @JvmStatic
//    fun setLocale(language: String, country: String = "") {
//        currentLocale = if (country.isEmpty()) {
//            Locale(language)
//        } else {
//            Locale(language, country)
//        }
//        resourceBundle = ResourceBundle.getBundle(BUNDLE, currentLocale)
//    }
//}







//
//
//
//
//package com.sensetime.sensecode.jetbrains.raccoon.resources
//
//import com.intellij.DynamicBundle
//import org.jetbrains.annotations.Nls
//import org.jetbrains.annotations.NonNls
//import org.jetbrains.annotations.PropertyKey
//import java.text.MessageFormat
//import java.util.Locale
//import java.util.ResourceBundle
//
//@NonNls
//private const val BUNDLE = "messages.RaccoonBundle"
//
//object RaccoonBundle {
//    private val bundle = ResourceBundle.getBundle(BUNDLE, Locale.getDefault())
//    @Nls
//    @JvmStatic
//    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any, locale: Locale = Locale.getDefault()): String {
//        val localizedBundle = ResourceBundle.getBundle(BUNDLE, locale)
////        return localizedBundle.getMessage(key, *params)
////        return localizedBundle.getString(key).format(*params)
//        val pattern = localizedBundle.getString(key)
//        return MessageFormat.format(pattern, *params)
//    }
//}