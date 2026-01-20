# MAList
MAList 🎬

​MAList is a simple and flexible application for creating lists. While I originally developed it to track anime, it can be used for anything, including TV shows or even shopping lists. This is my first project written in Kotlin, created purely for my own personal needs and preferences.  
​
✨ Features

​Versatile Lists: 

-Add titles for any type of content or items.

-​Rating System: Assign a 1 to 5-star rating to any entry.

​-Episode/Item Counter: Keep track of the number of episodes watched or items collected.

-​100% Local Storage: The app creates a dedicated folder named MyAnimeList in your device's Documents directory. All data and images are stored locally; no cloud synchronization is used.

​-One UI Inspired Design: The interface features a clean look with large headers and rounded corners inspired by Samsung's One UI.

​🛠 Technical Stack

​Language: Kotlin.  

​-UI: Jetpack Compose with Shared Element Transitions for smooth animations between screens.

-​Data Storage: Data is saved in JSON format using the Gson library.

​-Image Loading: Coil is used for efficient local image rendering.

-​Architecture: Uses ViewModel for state management.

​📂 Permissions

​The app requires the "Manage All Files" permission (MANAGE_EXTERNAL_STORAGE) to create its own directory structure and store the database and images directly on your device.
​License

​This project is licensed under the MIT License.
