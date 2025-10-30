package com.example.m.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.example.m",
            includeInStartupProfile = true
        ) {
            // Start the app
            pressHome()
            startActivityAndWait()

            // Wait for app to be fully loaded
            device.wait(Until.hasObject(By.text("Home")), 10000)
            Thread.sleep(2000) // Extra wait for full initialization

            // Scenario 1: Navigate to Library and scroll songs
            try {
                val libraryTab = device.findObject(By.text("Library"))
                libraryTab?.click()
                device.waitForIdle()
                Thread.sleep(1000)

                // Switch to Songs tab in Library
                val songsTab = device.findObject(By.text("Songs"))
                songsTab?.click()
                device.waitForIdle()
                Thread.sleep(1000)

                // Scroll through songs list multiple times
                // Look for the scrollable list with testTag or just the scrollable container
                val songsList = device.findObject(By.scrollable(true))
                if (songsList != null) {
                    device.waitForIdle()
                    Thread.sleep(500)

                    val bounds = songsList.visibleBounds
                    val centerX = bounds.centerX()
                    // Use more conservative scroll positions to avoid hitting navigation elements
                    val topY = bounds.top + (bounds.height() * 0.7).toInt()
                    val bottomY = bounds.top + (bounds.height() * 0.3).toInt()

                    // Scroll down
                    repeat(3) {
                        try {
                            device.swipe(centerX, topY, centerX, bottomY, 15)
                            device.waitForIdle()
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            // Ignore stale object exceptions during scrolling
                        }
                    }
                    // Scroll up
                    repeat(3) {
                        try {
                            device.swipe(centerX, bottomY, centerX, topY, 15)
                            device.waitForIdle()
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            // Ignore stale object exceptions during scrolling
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue even if this scenario fails
            }

            // Scenario 2: Play a song and interact with player hub
            try {
                // Scroll back to top to ensure we have songs visible
                val songsList = device.findObject(By.scrollable(true))
                if (songsList != null) {
                    val bounds = songsList.visibleBounds
                    val centerX = bounds.centerX()
                    val topY = bounds.top + (bounds.height() * 0.3).toInt()
                    val bottomY = bounds.top + (bounds.height() * 0.7).toInt()

                    // Scroll to top
                    repeat(5) {
                        try {
                            device.swipe(centerX, bottomY, centerX, topY, 20)
                            Thread.sleep(200)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }

                device.waitForIdle()
                Thread.sleep(1000)

                // Find and click a song in the MIDDLE of the screen to avoid navigation bars
                val allSongThumbnails = device.findObjects(By.descContains("Album art for"))
                var songClicked = false

                if (allSongThumbnails.isNotEmpty()) {
                    val screenHeight = device.displayHeight
                    val middleY = screenHeight / 2

                    // Find a song that's in the middle area of the screen (not near edges)
                    for (thumbnail in allSongThumbnails) {
                        try {
                            val songBounds = thumbnail.visibleBounds
                            val songCenterY = songBounds.centerY()

                            // Only click if song is in middle 50% of screen
                            if (songCenterY > screenHeight * 0.25 && songCenterY < screenHeight * 0.75) {
                                // Click on the left side of the song row (on the thumbnail/title area)
                                device.click(songBounds.left + 100, songBounds.centerY())
                                device.waitForIdle()
                                songClicked = true
                                break
                            }
                        } catch (e: Exception) {
                            // Try next song
                            continue
                        }
                    }
                }

                if (!songClicked) {
                    throw Exception("Could not find a song to click")
                }

                // Wait for player to initialize
                Thread.sleep(2000)

                // Open player hub by finding and clicking the mini player
                // Try to find the mini player by looking for playback control elements
                val screenHeight = device.displayHeight
                val screenWidth = device.displayWidth

                // Click higher up to avoid bottom navigation - mini player is typically at screenHeight - 200 to -250
                // Click in the left-center area where song title/artist is shown
                device.click(screenWidth / 3, screenHeight - 220)
                device.waitForIdle()
                Thread.sleep(1500)

                // Navigate to Queue tab
                val queueTab = device.findObject(By.text("UP NEXT"))
                queueTab?.click()
                device.waitForIdle()
                Thread.sleep(500)

                // Scroll through queue
                val queueList = device.findObject(By.scrollable(true))
                if (queueList != null) {
                    val bounds = queueList.visibleBounds
                    val centerX = bounds.centerX()
                    // Use conservative scroll positions to avoid navigation elements
                    val topY = bounds.top + (bounds.height() * 0.6).toInt()
                    val bottomY = bounds.top + (bounds.height() * 0.4).toInt()

                    repeat(5) {
                        try {
                            device.swipe(centerX, topY, centerX, bottomY, 10)
                            device.waitForIdle()
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            // Ignore stale object exceptions
                        }
                    }
                    repeat(3) {
                        try {
                            device.swipe(centerX, bottomY, centerX, topY, 10)
                            device.waitForIdle()
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            // Ignore stale object exceptions
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue even if this scenario fails
            }

            // Scenario 3: Switch between tabs in player hub
            try {
                val relatedTab = device.findObject(By.text("RELATED"))
                relatedTab?.click()
                device.waitForIdle()
                Thread.sleep(500)

                val lyricsTab = device.findObject(By.text("LYRICS"))
                lyricsTab?.click()
                device.waitForIdle()
                Thread.sleep(500)

                // Go back to queue
                val queueTab = device.findObject(By.text("UP NEXT"))
                queueTab?.click()
                device.waitForIdle()
                Thread.sleep(500)

                // Close player hub
                device.pressBack()
                device.waitForIdle()
            } catch (e: Exception) {
                // Continue even if this scenario fails
            }

            // Scenario 4: Navigate to Playlists
            try {
                Thread.sleep(500)
                val playlistsTab = device.findObject(By.text("Playlists"))
                playlistsTab?.click()
                device.waitForIdle()
                Thread.sleep(500)
            } catch (e: Exception) {
                // Continue
            }

            // Scenario 5: Navigate to Artists
            try {
                val artistsTab = device.findObject(By.text("Artists"))
                artistsTab?.click()
                device.waitForIdle()
                Thread.sleep(500)
            } catch (e: Exception) {
                // Continue
            }

            // Scenario 6: Go back to Songs and scroll again
            try {
                val songsTab = device.findObject(By.text("Songs"))
                songsTab?.click()
                device.waitForIdle()
                Thread.sleep(500)

                val songsList = device.findObject(By.scrollable(true))
                if (songsList != null) {
                    val bounds = songsList.visibleBounds
                    val centerX = bounds.centerX()
                    // Use conservative positions
                    val topY = bounds.top + (bounds.height() * 0.6).toInt()
                    val bottomY = bounds.top + (bounds.height() * 0.4).toInt()

                    repeat(2) {
                        try {
                            device.swipe(centerX, topY, centerX, bottomY, 10)
                            device.waitForIdle()
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue
            }

            // Scenario 7: Navigate to Home
            try {
                val homeTab = device.findObject(By.text("Home"))
                homeTab?.click()
                device.waitForIdle()
                Thread.sleep(500)

                // Scroll through home screen
                val homeList = device.findObject(By.scrollable(true))
                if (homeList != null) {
                    val bounds = homeList.visibleBounds
                    val centerX = bounds.centerX()
                    // Use conservative positions to avoid hitting navigation
                    val topY = bounds.top + (bounds.height() * 0.6).toInt()
                    val bottomY = bounds.top + (bounds.height() * 0.4).toInt()

                    try {
                        device.swipe(centerX, topY, centerX, bottomY, 10)
                        device.waitForIdle()
                        Thread.sleep(300)
                        device.swipe(centerX, bottomY, centerX, topY, 10)
                        device.waitForIdle()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            } catch (e: Exception) {
                // Continue
            }

            // Scenario 8: Navigate to Search
            try {
                val searchTab = device.findObject(By.text("Search"))
                searchTab?.click()
                device.waitForIdle()
                Thread.sleep(500)
            } catch (e: Exception) {
                // Final scenario - just continue
            }
        }
    }
}

