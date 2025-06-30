package com.ai.assistance.operit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.pointerInput
import com.ai.assistance.dragonbones.DragonBonesController
import com.ai.assistance.dragonbones.DragonBonesModel
import com.ai.assistance.dragonbones.DragonBonesViewCompose
import com.ai.assistance.operit.data.model.ModelType
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// --- Animation Names ---
// Common animations applicable to all model types
const val IK_TARGET_BONE_NAME = "ik_target"
const val IDLE_ANIMATION_NAME = "idle"
const val TAP_REACTION_ANIMATION_NAME = "tap_reaction"

// Common random animations for idle state
val RANDOM_IDLE_ANIMATIONS = listOf("blink", "shake_head", "wag_tail")

// Q-Pet specific state animations
const val QPET_WALK_ANIMATION = "walk"
const val QPET_RUN_ANIMATION = "run"
const val QPET_JUMP_ANIMATION = "jump"
const val QPET_FALL_ANIMATION = "fall"

/**
 * Represents the movement state of the pet. Only used for [ModelType.QPET].
 */
enum class PetMovementState {
    IDLE,
    WALKING,
    RUNNING
}

/**
 * A unified, managed wrapper for [DragonBonesViewCompose] that handles different animation logics.
 *
 * This component automatically handles animation logic based on the provided [modelType].
 * - [ModelType.STANDARD]: 'idle' and 'blink' animations, tap reactions, and IK head-following.
 * - [ModelType.QPET]: State-based animations for idle, walk, run, jump, fall, random actions, tap reactions, and IK.
 *
 * @param model The [DragonBonesModel] to be displayed.
 * @param controller The [DragonBonesController] to interact with the view.
 * @param modifier The modifier to be applied to the component.
 * @param modelType The type of animation logic to apply.
 * @param enableGestures A boolean indicating whether to enable IK and tap gestures.
 * @param enablePetMovement A boolean indicating whether to enable the special Q-Pet movement and interaction gestures.
 * @param zOrderOnTop Whether the surface view is placed on top of its window.
 * @param onError A callback for when an error occurs during loading or playback.
 * @param movementState The current movement state of the pet (for QPET mode, when enablePetMovement is false).
 * @param jumpTrigger A value that, when changed, triggers the jump animation (for QPET mode, when enablePetMovement is false).
 * @param fallTrigger A value that, when changed, triggers the fall animation (for QPET mode, when enablePetMovement is false).
 */
@Composable
fun ManagedDragonBonesView(
    model: DragonBonesModel,
    controller: DragonBonesController,
    modifier: Modifier = Modifier,
    modelType: ModelType,
    enableGestures: Boolean = true,
    enablePetMovement: Boolean = true,
    zOrderOnTop: Boolean = true,
    onError: (String) -> Unit,
    // QPET-specific parameters (for external control)
    movementState: PetMovementState = PetMovementState.IDLE,
    jumpTrigger: Any? = null,
    fallTrigger: Any? = null
) {
    // This key will reset all animation effects when the model or its type changes.
    val animationLogicKey = remember(model, modelType) { Any() }
    val coroutineScope = rememberCoroutineScope()

    // Local state for pet movement, only used when enablePetMovement is true.
    var petMovementState by remember(animationLogicKey) { mutableStateOf(PetMovementState.IDLE) }

    // Decide which movement state to use.
    val currentMovementState = if (enablePetMovement) petMovementState else movementState
    
    // Base animation effect
    LaunchedEffect(animationLogicKey, controller.animationNames, currentMovementState) {
        if (controller.animationNames.isEmpty()) return@LaunchedEffect

        val targetAnimation = when (modelType) {
            ModelType.STANDARD -> IDLE_ANIMATION_NAME
            ModelType.QPET -> when (currentMovementState) {
                PetMovementState.IDLE -> IDLE_ANIMATION_NAME
                PetMovementState.WALKING -> QPET_WALK_ANIMATION
                PetMovementState.RUNNING -> QPET_RUN_ANIMATION
            }
        }
        
        if (controller.animationNames.contains(targetAnimation)) {
            controller.fadeInAnimation(targetAnimation, layer = 0, loop = 0)
        }
    }

    // Random periodic animation effect
    LaunchedEffect(animationLogicKey, controller.animationNames) {
        if (controller.animationNames.isEmpty()) return@LaunchedEffect

        val availableAnims = RANDOM_IDLE_ANIMATIONS.filter { controller.animationNames.contains(it) }
        if (availableAnims.isNotEmpty()) {
            while (true) {
                delay(Random.nextLong(2000, 8000))
                controller.fadeInAnimation(availableAnims.random(), layer = 1, loop = 1)
            }
        }
    }

    // One-shot trigger effects (for external control, or internal jump/fall sequence)
    if (modelType == ModelType.QPET) {
        LaunchedEffect(jumpTrigger) {
            if (jumpTrigger != null && controller.animationNames.contains(QPET_JUMP_ANIMATION)) {
                controller.fadeInAnimation(QPET_JUMP_ANIMATION, layer = 2, loop = 1)
            }
        }
        LaunchedEffect(fallTrigger) {
            if (fallTrigger != null && controller.animationNames.contains(QPET_FALL_ANIMATION)) {
                controller.fadeInAnimation(QPET_FALL_ANIMATION, layer = 2, loop = 1)
            }
        }
    }

    val gestureModifier = if (enableGestures) {
        if (modelType == ModelType.QPET && enablePetMovement) {
            // New, advanced gesture handler for QPET movement
            Modifier
                .pointerInput(animationLogicKey) {
                    var totalDragOffset = Offset.Zero
                    var dragStartTime = 0L

                    detectDragGestures(
                        onDragStart = {
                            totalDragOffset = Offset.Zero
                            dragStartTime = System.currentTimeMillis()
                            petMovementState = PetMovementState.WALKING
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragOffset += dragAmount
                            // Move the pet
                            controller.translationX += dragAmount.x
                            controller.translationY += dragAmount.y
                            // IK follows finger
                             try { controller.overrideBonePosition(IK_TARGET_BONE_NAME, change.position.x, change.position.y) } catch (e: Exception) {}
                        },
                        onDragEnd = {
                            val dragDuration = System.currentTimeMillis() - dragStartTime
                            // Heuristics for swipe up: must be fast, vertical, and of a minimum distance
                            val swipeVelocityY = if(dragDuration > 0) totalDragOffset.y / dragDuration else 0f
                            if (swipeVelocityY < -1.5f && totalDragOffset.y < -150f && abs(totalDragOffset.y) > 2 * abs(totalDragOffset.x)) {
                                coroutineScope.launch {
                                    petMovementState = PetMovementState.IDLE // Stop walking
                                    if (controller.animationNames.contains(QPET_JUMP_ANIMATION)) {
                                        controller.fadeInAnimation(QPET_JUMP_ANIMATION, layer = 2, loop = 1)
                                    }
                                    delay(1000) // Assumed jump anim duration
                                    if (controller.animationNames.contains(QPET_FALL_ANIMATION)) {
                                        controller.fadeInAnimation(QPET_FALL_ANIMATION, layer = 2, loop = 1)
                                    }
                                    delay(500) // Assumed fall anim duration
                                    petMovementState = PetMovementState.IDLE
                                }
                            } else {
                                // It was a regular drag, just go back to idle
                                petMovementState = PetMovementState.IDLE
                            }
                            try { controller.resetBone(IK_TARGET_BONE_NAME) } catch(e: Exception) {}
                        }
                    )
                }
                .pointerInput(animationLogicKey) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (controller.animationNames.contains(TAP_REACTION_ANIMATION_NAME)) {
                                controller.fadeInAnimation(TAP_REACTION_ANIMATION_NAME, layer = 2, loop = 1)
                            }
                        },
                        onTap = { offset ->
                            coroutineScope.launch {
                                petMovementState = PetMovementState.WALKING
                                val startOffset = Offset(controller.translationX, controller.translationY)
                                Animatable(0f).animateTo(1f, animationSpec = tween(1000)) {
                                    val currentPos = lerp(startOffset, offset, this.value)
                                    controller.translationX = currentPos.x
                                    controller.translationY = currentPos.y
                                    try { controller.overrideBonePosition(IK_TARGET_BONE_NAME, offset.x, offset.y) } catch (e: Exception) {}
                                }
                                petMovementState = PetMovementState.IDLE
                                try { controller.resetBone(IK_TARGET_BONE_NAME) } catch (e: Exception) {}
                            }
                        }
                    )
                }
        } else {
            // Original, simpler gesture handler, now fixed to consume events correctly.
            Modifier
                .pointerInput(animationLogicKey) {
                    detectTapGestures(
                        onTap = {
                            if (controller.animationNames.contains(TAP_REACTION_ANIMATION_NAME)) {
                                controller.fadeInAnimation(TAP_REACTION_ANIMATION_NAME, layer = 2, loop = 1)
                            }
                        }
                    )
                }
                .pointerInput(animationLogicKey) {
                    // Use detectDragGestures for IK to ensure proper event consumption.
                    // This prevents events from propagating to parent scrollers.
                    detectDragGestures(
                        onDragStart = { offset ->
                            try {
                                controller.overrideBonePosition(IK_TARGET_BONE_NAME, offset.x, offset.y)
                            } catch (e: Exception) { /* Bone might not exist, ignore */ }
                        },
                        onDrag = { change, _ ->
                            // Consume the change and update the bone position.
                            change.consume()
                            try {
                                controller.overrideBonePosition(IK_TARGET_BONE_NAME, change.position.x, change.position.y)
                            } catch (e: Exception) { /* Bone might not exist, ignore */ }
                        },
                        onDragEnd = {
                            try {
                                controller.resetBone(IK_TARGET_BONE_NAME)
                            } catch (e: Exception) { /* Bone might not exist, ignore */ }
                        },
                        onDragCancel = {
                            try {
                                controller.resetBone(IK_TARGET_BONE_NAME)
                            } catch (e: Exception) { /* Bone might not exist, ignore */ }
                        }
                    )
                }
        }
    } else {
        Modifier
    }

    DragonBonesViewCompose(
        modifier = modifier.then(gestureModifier),
        model = model,
        controller = controller,
        zOrderOnTop = zOrderOnTop,
        onError = onError
    )
}