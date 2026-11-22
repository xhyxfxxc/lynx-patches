/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.tweet

import app.crimera.patches.twitter.entity.tweetInfo.TweetInfoObjectFingerprint
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val tweetEntityPatch =
    patch(
        description = "For tweet entity reflection",
    ) {
        execute {
            val (profileNameMethod, userNameMethod) =
                with(TweetNamesFingerprint) {
                    Pair(
                        instructionMatches[0].instruction.getReference<MethodReference>()!!,
                        instructionMatches[2].instruction.getReference<MethodReference>()!!,
                    )
                }

            TweetUsernameFingerprint.changeFirstString(userNameMethod.name)
            TweetProfileNameFingerprint.changeFirstString(profileNameMethod.name)

            val tweetObjectMethods = TweetObjectFingerprint.classDef.methods

            // Lynx: on X 12.19.1 the fingerprint matches more than one class (g1 and p). Take all
            // of them and require that exactly one tweet object method returns one of them.
            val textObjectTypes =
                GetTextFromTextObjectFingerprint
                    .matchAll()
                    .map { it.originalClassDef.type }
                    .toSet()
            val textObjectMethods = tweetObjectMethods.filter { it.returnType in textObjectTypes }
            val getTextObjectFromTweetObjectMethodName =
                textObjectMethods.singleOrNull()?.name
                    ?: throw PatchException(
                        "Expected exactly one tweet object method returning a text object, found " +
                            "${textObjectMethods.size} (text object classes: $textObjectTypes)",
                    )
            TweetShortTextFingerprint.changeFirstString(getTextObjectFromTweetObjectMethodName)

            val getTweetUserIdMethod =
                tweetObjectMethods
                    .first { it.returnType == "J" }
                    .name
            TweetUserIdFingerprint.changeFirstString(getTweetUserIdMethod)

            val getMediaObjectMethod =
                tweetObjectMethods.firstOrNull { methodDef ->
                    methodDef.implementation
                        ?.instructions
                        ?.map { it.opcode }
                        ?.toList() ==
                        listOf(
                            Opcode.IGET_OBJECT,
                            Opcode.IGET_OBJECT,
                            Opcode.IGET_OBJECT,
                            Opcode.IGET_OBJECT,
                            Opcode.RETURN_OBJECT,
                        )
                } ?: throw PatchException("getMediaObject not found")
            TweetExtMediaEntitiesListFingerprint.changeFirstString(getMediaObjectMethod.name)

            val extMediaListField =
                TweetMediaEntityClassFingerprint.classDef.fields
                    .first { it.type.contains("List") }
                    .name
            TweetExtMediaEntitiesListFingerprint.changeStringAt(1, extMediaListField)

            val getNoteTweetMethod =
                tweetObjectMethods
                    .firstOrNull { it.returnType.contains("notetweet") }
                    ?.name
                    ?: throw PatchException("getNoteTweetMethod not found")
            TweetLongTextFingerprint.changeFirstString(getNoteTweetMethod)

            val longTextField =
                LongTweetObjectFingerprint.classDef.fields
                    .first { it.type == "Ljava/lang/String;" }
                    .name
            TweetLongTextFingerprint.changeStringAt(1, longTextField)

            TweetInfoObjectFingerprint.apply {
                val langStrIndex = stringMatches[1].index
                method.apply {
                    val className = extensionToClassName(getInstruction(langStrIndex + 1).fieldExtractor().definingClass)
                    val infoField =
                        TweetObjectFingerprint.classDef.fields
                            .first {
                                it.type == className
                            }.name

                    TweetInfoFingerprint.changeFirstString(infoField)
                }
            }
        }
    }
