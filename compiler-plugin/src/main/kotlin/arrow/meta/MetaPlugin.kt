package arrow.meta

import arrow.meta.autofold.autoFold
import arrow.meta.extensions.ExtensionPhase
import arrow.meta.extensions.MetaComponentRegistrar
import arrow.meta.higherkind.higherKindedTypes
import arrow.meta.typeclasses.typeClasses

class MetaPlugin : MetaComponentRegistrar {
  override fun intercept(): List<ExtensionPhase> =
    higherKindedTypes +
      autoFold
  //typeClasses
}