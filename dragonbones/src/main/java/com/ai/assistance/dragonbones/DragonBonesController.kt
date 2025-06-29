package com.ai.assistance.dragonbones

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A controller for the [DragonBonesViewCompose] composable.
 *
 * This controller provides a way to interact with the DragonBones view, such as playing animations
 * and retrieving the list of available animations.
 *
 * @see rememberDragonBonesController
 */
@Stable
class DragonBonesController(private val coroutineScope: CoroutineScope) {

    private var view: DragonBonesView? = null

    /**
     * The list of available animation names for the current model. This state is updated
     * automatically when a new model is loaded.
     */
    var animationNames by mutableStateOf<List<String>>(emptyList())

    internal var animationToPlay by mutableStateOf<String?>(null)

    internal var fadeInTime by mutableStateOf(0.0f)

    /** The overall scale of the armature. Default is 0.5f. */
    var scale by mutableStateOf(0.5f)

    /** The horizontal translation of the armature from the center. */
    var translationX by mutableStateOf(0.0f)

    /** The vertical translation of the armature from the center. */
    var translationY by mutableStateOf(0.0f)

    /**
     * A callback that is invoked when a slot is tapped. The string parameter is the name of the
     * tapped slot.
     */
    var onSlotTap: ((String) -> Unit)? = null

    internal fun setView(dragonBonesView: DragonBonesView?) {
        view = dragonBonesView
        if (dragonBonesView != null) {
            dragonBonesView.onSlotTapListener = { slotName -> onSlotTap?.invoke(slotName) }
            coroutineScope.launch {
                dragonBonesView.getAnimationNames { names -> animationNames = names }
            }
        } else {
            animationNames = emptyList()
        }
    }

    /**
     * Plays the specified animation.
     *
     * @param name The name of the animation to play.
     */
    fun playAnimation(name: String, fadeInTime: Float = 0.3f) {
        // Use a timestamp to ensure the animation can be re-triggered
        this.fadeInTime = fadeInTime
        animationToPlay = "$name:${System.currentTimeMillis()}"
    }

    fun overrideBonePosition(boneName: String, x: Float, y: Float) {
        view?.overrideBonePosition(boneName, x, y)
    }

    fun resetBone(boneName: String) {
        view?.resetBone(boneName)
    }

    internal fun onAnimationPlayed() {
        animationToPlay = null
    }
}

/**
 * Creates and remembers a [DragonBonesController].
 *
 * @return A new [DragonBonesController] instance.
 */
@Composable
fun rememberDragonBonesController(): DragonBonesController {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    return remember { DragonBonesController(coroutineScope) }
}
