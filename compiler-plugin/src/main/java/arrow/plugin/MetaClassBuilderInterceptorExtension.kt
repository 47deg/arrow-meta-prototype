package arrow.plugin

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.coroutines.isSuspendLambdaOrLocalFunction
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isNothingOrNullableNothing
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.org.objectweb.asm.MethodVisitor

internal class MetaClassBuilder(
  val messageCollector: MessageCollector,
  private val builder: ClassBuilder,
  private val bindingContext: BindingContext
) : DelegatingClassBuilder() {
  override fun getDelegate(): ClassBuilder = builder

  override fun newMethod(
    origin: JvmDeclarationOrigin,
    access: Int,
    name: String,
    desc: String,
    signature: String?,
    exceptions: Array<out String>?
  ): MethodVisitor {
    println("ClassBuilderInterceptorExtension.DelegatingClassBuilder.newMethod, origin: ${origin.descriptor}")
    //delegate to the parent method visitor for construction
    val original: MethodVisitor = super.newMethod(origin, access, name, desc, signature, exceptions)
    //bail quickly if this is not a function
    val function: FunctionDescriptor = origin.descriptor as? FunctionDescriptor ?: return original
    val functionName = function.name.asString()
    //we ignore suspend functions as they are already safe
    if (!function.isSuspend &&
      !function.isSynthesized &&
      !function.isSuspendLambdaOrLocalFunction() &&
      functionName != "<init>" &&
      !functionName.startsWith("<get") &&
      !functionName.startsWith("<set")) {
      val functionPsi = function.findPsi()
      //if the function returns Unit then it should have been suspended since all it can do is produce effects
      if (function.returnType?.isUnit() == true || function.returnType?.isNothingOrNullableNothing() == true) {
        functionPsi.let {
          messageCollector.report(
            ERROR,
            "Unit or Nothing return on a non suspended function: ${function.name}",
            MessageUtil.psiElementToMessageLocation(it)
          )
        }
      } else functionPsi?.checkPurity(function)
    }
    return original
  }

  private fun PsiElement.checkPurity(descriptor: FunctionDescriptor) {
    accept(object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement?) {
        if (element is KtCallExpression) {
          checkExpressionPurity(descriptor, element)
        } else if (element is KtThrowExpression) {
          messageCollector.report(
            ERROR,
            "Impure expression in function ${descriptor.name}: `${element.text}` returning `Nothing` due to `throw` expression only allowed in `suspend` functions.\n" +
              "Generally, it is not recommended to `throw` Exceptions and instead to represent exceptional cases with datatypes and typeclasses as described https://next.arrow-kt.io/docs/patterns/error_handling/",
            MessageUtil.psiElementToMessageLocation(element)
          )
        }
        element?.acceptChildren(this)
      }
    })
  }

  private fun checkExpressionPurity(descriptor: FunctionDescriptor, expression: KtExpression) {
    val expressionRetType: KotlinType? = expression.getType(bindingContext)
    if (expressionRetType?.isUnit() == true || expressionRetType?.isNothingOrNullableNothing() == true) {
      messageCollector.report(
        ERROR,
        "Impure expression in function ${descriptor.name}: `${expression.text}` returning `Unit` or `Nothing` only allowed in `suspend` functions",
        MessageUtil.psiElementToMessageLocation(expression)
      )
    }
  }


}

class MetaClassBuilderInterceptorExtension(val messageCollector: MessageCollector) : ClassBuilderInterceptorExtension {

  override fun interceptClassBuilderFactory(
    interceptedFactory: ClassBuilderFactory,
    bindingContext: BindingContext,
    diagnostics: DiagnosticSink
  ): ClassBuilderFactory = object : ClassBuilderFactory by interceptedFactory {
    override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder {
      println("ClassBuilderInterceptorExtension.newClassBuilder, origin: ${origin.descriptor}")
      val builder = MetaClassBuilder(messageCollector, interceptedFactory.newClassBuilder(origin), bindingContext)
      return builder
    }
  }

}