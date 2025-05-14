package com.example.barcodescannerapp

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Gallery : Screen("gallery")
}
