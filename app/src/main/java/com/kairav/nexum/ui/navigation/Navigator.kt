package com.kairav.nexum.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // This is a top level route, just switch to it.
            if (state.topLevelRoute == route) {
                // If already on this tab, clear its stack back to the root (the tab itself).
                val stack = state.backStacks[route]
                while (stack != null && stack.last() != route) {
                    stack.removeLastOrNull()
                }
            } else {
                state.topLevelRoute = route
            }
        } else {
            // Child route, add to the current top level route's backstack.
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
