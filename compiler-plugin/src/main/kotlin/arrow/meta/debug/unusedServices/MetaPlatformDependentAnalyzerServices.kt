package arrow.meta.debug.unusedServices

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.PlatformConfigurator
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformConfigurator
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import java.util.ArrayList

/**
 * similar to PlatformDependentAnalyzerServices we can inject Imports in Analysis Phase
 * That's why there are no import's like kotlin.collections.listOf evenThough we use that in many occasions
 *
 * the https://github.com/JetBrains/kotlin/blob/master/compiler/frontend.common/src/org/jetbrains/kotlin/resolve/PlatformDependentAnalyzerServices.kt
 *
 * still looking for a way to extend from PlatformDependentAnalyzerServices.kt, because it resolves internals like this for MPP
 * val TargetPlatform.findAnalyzerServices: PlatformDependentAnalyzerServices
 * get() = when {
 * isJvm() -> JvmPlatformAnalyzerServices
 * isJs() -> JsPlatformAnalyzerServices
 * isNative() -> NativePlatformAnalyzerServices
 * isCommon() -> CommonPlatformAnalyzerServices
 * else -> throw IllegalStateException("Unknown platform $this")
 * }
 */
abstract class MetaPlatformDependentAnalyzerServices {
  private data class DefaultImportsKey(val includeKotlinComparisons: Boolean, val includeLowPriorityImports: Boolean)

  private val defaultImports = LockBasedStorageManager("TargetPlatform").let { storageManager ->
    storageManager.createMemoizedFunction<DefaultImportsKey, List<ImportPath>> { (includeKotlinComparisons, includeLowPriorityImports) ->
      ArrayList<ImportPath>().apply {
        listOf(
          // Default imports in Kotlin
          "kotlin.*",
          "arrow.core.*" // not working yet
          // "kotlin.annotation.*",
          // "kotlin.collections.*",
          // "kotlin.ranges.*",
          // "kotlin.sequences.*",
          // "kotlin.text.*",
          // "kotlin.io.*"
          // Now we can add all annotations via the compiler plugin
          // "arrow.with",
          // "arrow.annotations.*"
        ).forEach { add(ImportPath.fromString(it)) }

        if (includeKotlinComparisons) {
          add(ImportPath.fromString("kotlin.comparisons.*"))
        }

        computePlatformSpecificDefaultImports(storageManager, this)

        if (includeLowPriorityImports) {
          addAll(defaultLowPriorityImports)
        }
      }
    }
  }

  abstract val platformConfigurator: PlatformConfigurator

  open val defaultLowPriorityImports: List<ImportPath> get() = emptyList()

  fun getDefaultImports(languageVersionSettings: LanguageVersionSettings, includeLowPriorityImports: Boolean): List<ImportPath> =
    defaultImports(
      DefaultImportsKey(
        languageVersionSettings.supportsFeature(LanguageFeature.DefaultImportOfPackageKotlinComparisons),
        includeLowPriorityImports
      )
    )

  protected abstract fun computePlatformSpecificDefaultImports(storageManager: StorageManager, result: MutableList<ImportPath>)

  open val excludedImports: List<FqName> get() = emptyList()

  open fun dependencyOnBuiltIns(): ModuleInfo.DependencyOnBuiltIns =
    ModuleInfo.DependencyOnBuiltIns.LAST
}

/**
 * As an Example implementation of a Platform consider this internal Jvm version in the compiler:
 * Another example is the JsPlatformAnalyzerServices
 */
/*
object JvmPlatformAnalyzerServices : PlatformDependentAnalyzerServices() {
  override fun computePlatformSpecificDefaultImports(storageManager: StorageManager, result: MutableList<ImportPath>) {
    result.add(ImportPath.fromString("kotlin.jvm.*"))

    fun addAllClassifiersFromScope(scope: MemberScope) {
      for (descriptor in scope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS, MemberScope.ALL_NAME_FILTER)) {
        result.add(ImportPath(DescriptorUtils.getFqNameSafe(descriptor), false))
      }
    }

    for (builtInPackage in JvmBuiltIns(storageManager, JvmBuiltIns.Kind.FROM_CLASS_LOADER).builtInPackagesImportedByDefault) {
      addAllClassifiersFromScope(builtInPackage.memberScope)
    }
  }

  override val defaultLowPriorityImports: List<ImportPath> = listOf(ImportPath.fromString("java.lang.*"))

  override val platformConfigurator: PlatformConfigurator = JvmPlatformConfigurator
}*/

object ArrowPlatformAnalyzerServices : MetaPlatformDependentAnalyzerServices() {

  override val defaultLowPriorityImports: List<ImportPath> = listOf(ImportPath.fromString("arrow.core..lang.*"))
  override val platformConfigurator: PlatformConfigurator
    get() = JvmPlatformConfigurator

  override fun computePlatformSpecificDefaultImports(storageManager: StorageManager, result: MutableList<ImportPath>) {
    result.add(ImportPath.fromString("arrow.core.*"))
    println("ArrowPlatformAnalyzerServices.computePlatformSpecificDefaultImports: $storageManager")
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}