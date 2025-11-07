# Google Drive Integration Features

This document describes the three main Google Drive integration features implemented in the FileEncryptor application.

## Overview

The FileEncryptor application now supports seamless integration with Google Drive for backing up and restoring encrypted files. All three features are fully implemented and ready to use.

---

## 1️⃣ Backup Button

### Location
- **Dashboard**: Main window - Click the **"Backup to Google Drive"** button in the action buttons row
- **FXML**: `src/main/resources/fxml/main.fxml` - Line with `onAction="#handleBackupToGoogleDrive"`

### Functionality

When you click the **Backup to Google Drive** button:

1. **Authentication**
   - The app checks if you're already signed in to your Google account
   - If not authenticated, it will prompt you to open your browser for login
   - After successful authentication, your credentials are cached locally for future use

2. **File Selection**
   - **Auto-backup mode**: If "Auto-backup all" checkbox is selected, ALL files from the dashboard will be backed up
   - **Manual selection mode**: If the checkbox is not selected, only the selected files (via checkboxes in the table) will be backed up
   - If no files are selected and auto-backup is off, the operation does nothing

3. **Duplicate Prevention**
   - The app queries your Google Drive backup folder to get a list of existing backups
   - Only NEW or MODIFIED files are uploaded
   - Files that are already backed up with the same name are SKIPPED (no duplicates)

4. **Metadata Preservation**
   - For each file being backed up, the app stores encryption metadata in Google Drive's appProperties:
     - **IV** (Initialization Vector)
     - **Salt** (for key derivation)
     - **Encryption Algorithm** (e.g., "AES-GCM-256")
     - **Compression Flag** (Y/N)
     - **Original Filename**
     - **File Size**
     - **Owner ID**

5. **Real-time Progress Updates**
   - **Current file progress bar**: Shows how much of the current file has been uploaded
   - **Current file label**: Displays the name of the file being uploaded
   - **Overall progress bar**: Shows cumulative progress across all files
   - **Overall progress percentage**: Displays the completion percentage
   - All updates happen dynamically while the backup is running

6. **Completion**
   - Once all files are backed up, a success notification appears with a summary
   - You can continue using the app while backup runs (it's in a background thread)

### Storage Location
- Backups are stored in a folder named **"FileEncryptor Backups"** in your Google Drive
- All metadata is preserved in the Drive file's appProperties for later restoration

### Example Workflow
```
1. User clicks "Backup to Google Drive"
2. App authenticates with Google (if needed)
3. App shows: "Uploading: photo.jpg.encrypted — 45%"
4. Progress bars update in real-time
5. Notification: "Backup complete: Uploaded 5 of 5 file(s)"
```

---

## 2️⃣ Restore Button

### Location
- **Dashboard**: Main window - Click the **"Restore from Google Drive"** button in the action buttons row
- **FXML**: `src/main/resources/fxml/main.fxml` - Line with `onAction="#handleRestoreFromGoogleDrive"`

### Functionality

When you click the **Restore from Google Drive** button:

1. **Authentication**
   - Similar to backup, the app authenticates with Google if needed
   - Uses cached credentials if already authenticated

2. **File Discovery**
   - The app fetches the list of all files in your "FileEncryptor Backups" folder
   - If "Auto-backup all" is selected, ALL backup files are fetched
   - If "Auto-backup all" is NOT selected, only files matching selected items (by stored filename) are restored

3. **Duplicate Prevention**
   - The app checks the local encrypted files directory for existing files
   - Files that ALREADY EXIST locally are SKIPPED (no overwriting)
   - Only MISSING files are downloaded and restored

4. **Metadata Reconstruction**
   - For each restored file, the app reads the appProperties from the Google Drive file
   - Using this metadata, it reconstructs the FileMetadata database entries:
     - Sets the owner ID to the current user
     - Restores the original filename, IV, salt, encryption algorithm, etc.
     - Marks files as compressed if applicable
     - Records the file size

5. **Database Integration**
   - Restored files are automatically added to the database
   - Duplicate entries are skipped (files already in the DB)
   - Missing metadata is reported but doesn't block the restore

6. **Real-time Progress Updates**
   - **Current file label**: "Downloading: document.pdf.encrypted"
   - **File progress bar**: Shows download progress for current file
   - **Overall progress**: Cumulative download percentage across all files
   - All updates happen dynamically

7. **Completion**
   - Once all files are restored, a success notification shows:
     - Number of files downloaded
     - Number of files added to dashboard
     - Number of skipped files (duplicates/missing metadata)
   - The dashboard table automatically refreshes to show restored files

### Storage Location
- Files are restored to the configured encrypted storage directory
- The local storage path is shown on the dashboard as "Storage:" label

### Example Workflow
```
1. User clicks "Restore from Google Drive"
2. App fetches list of backup files: 3 files found
3. App checks local directory: 1 file already exists (skipped)
4. App shows: "Downloading: video.mp4.encrypted — 67%"
5. Progress bars update in real-time
6. Notification: "Restore complete: Restored 2 files, added 2 to dashboard"
7. Dashboard table auto-refreshes with new files
```

---

## 3️⃣ Sign Out Option

### Location
- **Dashboard**: Top bar, next to "Settings" button - Click the **"Sign Out Google"** button
- **FXML**: `src/main/resources/fxml/main.fxml` - Added as a new button with red styling
- **Handler**: `MainController.java` - `handleGoogleSignOut()` method

### Functionality

When you click the **Sign Out Google** button:

1. **Confirmation Dialog**
   - A confirmation dialog appears asking if you're sure
   - The dialog explains what will happen:
     - Cached Google authentication will be cleared
     - You'll need to re-authenticate on next backup/restore operation

2. **Token Clearance**
   - If you confirm, the app calls `GoogleDriveAuth.signOut()`
   - This method:
     - Deletes all cached OAuth tokens from `~/.fileencryptor/google-tokens/`
     - Removes the tokens directory itself
     - Clears any in-memory credentials

3. **Success Notification**
   - A success notification confirms: "Successfully signed out from your Google account. Cached tokens have been cleared."
   - You are now completely disconnected from Google Drive

4. **Next Authentication**
   - The next time you click Backup or Restore, you'll need to authenticate again
   - A fresh browser window will open for you to log in
   - A new token cache will be created

### Visual Design
- Button color: **Red** (#FF6B6B) with white text - clearly distinguishes it from other actions
- Button location: Right side of top bar, between "Sign Out" (application logout) and "Settings"

### Example Workflow
```
1. User clicks "Sign Out Google" button
2. Confirmation dialog appears
3. User clicks OK
4. App clears cached tokens from ~/.fileencryptor/google-tokens/
5. Notification: "Successfully signed out from your Google account..."
6. Next backup/restore attempt will prompt for Google login
```

---

## Implementation Details

### Files Modified/Created

1. **main.fxml**
   - Added "Backup to Google Drive" button (already existed)
   - Added "Restore from Google Drive" button (already existed)
   - Added "Sign Out Google" button (NEW)

2. **MainController.java**
   - Added `handleGoogleSignOut()` method (NEW)
   - Existing methods: `handleBackupToGoogleDrive()`, `handleRestoreFromGoogleDrive()`

3. **GoogleDriveAuth.java** (No changes needed)
   - Existing method: `getDriveService()` - Handles authentication
   - Existing method: `signOut()` - Clears cached tokens
   - Existing method: `isAuthenticated()` - Checks if already signed in

4. **GoogleDriveBackupService.java** (No changes needed)
   - Handles all backup/restore operations with Google Drive API
   - Manages appProperties for metadata storage

---

## Configuration Requirements

### Google Drive API Credentials
To use these features, you need a Google OAuth 2.0 credentials file:

1. **Location**: `src/main/resources/google/credentials.json`
2. **How to obtain**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project
   - Enable the Google Drive API
   - Create OAuth 2.0 Desktop Application credentials
   - Download the credentials JSON file
   - Place it in the above location

### Token Storage
- Cached tokens are stored in: `~/.fileencryptor/google-tokens/`
- These are created automatically on first authentication
- Can be manually cleared by clicking "Sign Out Google"

---

## Error Handling

### Common Errors

1. **"Missing /google/credentials.json"**
   - Solution: Add your Google OAuth credentials file to `src/main/resources/google/credentials.json`

2. **Authentication fails**
   - Solution: Check your internet connection and ensure credentials.json is valid

3. **"All selected files are already backed up"**
   - Solution: This is expected if you're backing up the same files again without modifying them

4. **"No matching files found in Drive backup"**
   - Solution: No files have been backed up yet, or auto-backup is off and nothing is selected

### Error Notifications
All errors are displayed in red notification dialogs with descriptive messages. Check these messages for troubleshooting.

---

## Usage Tips

1. **First-time setup**
   - Set your storage directory via "Change..." button next to "Storage:"
   - Set your decrypt output directory (optional)

2. **Backup best practices**
   - Use "Auto-backup all" for periodic backups of all files
   - Use manual selection for backing up specific files
   - Your files remain encrypted locally even after backup

3. **Restore best practices**
   - Restore is safe - it won't overwrite existing local files
   - Your encrypted files remain in Google Drive indefinitely
   - After restore, you can decrypt files using the "Decrypt File" button

4. **Privacy & Security**
   - All files are encrypted before being backed up
   - Encryption keys are stored locally (not in Google Drive)
   - Only the encrypted data + metadata is stored in Google Drive
   - You control your own encryption keys

---

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│           JavaFX UI (Dashboard)             │
│  ┌───────────────────────────────────────┐  │
│  │  Backup  │  Restore  │  Sign Out     │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                      ↓
        ┌─────────────────────────┐
        │  MainController         │
        │  (Handles user input)   │
        └─────────────────────────┘
                      ↓
    ┌──────────────────┴──────────────────┐
    ↓                                      ↓
┌──────────────────┐          ┌──────────────────┐
│GoogleDriveAuth   │          │GoogleDrive       │
│ • getDriveService          │BackupService     │
│ • signOut        │          │ • upload         │
│ • isAuthenticated          │ • download       │
└──────────────────┘          └──────────────────┘
    ↓                                      ↓
┌──────────────────┐          ┌──────────────────┐
│Google OAuth 2.0  │          │Google Drive API  │
│(Browser Login)   │          │(File operations) │
└──────────────────┘          └──────────────────┘
```

---

## Testing Checklist

- [ ] Click "Backup to Google Drive" - should prompt authentication on first use
- [ ] Select a file and backup - should upload to Google Drive
- [ ] Click "Backup to Google Drive" again - should skip already backed up files
- [ ] Click "Restore from Google Drive" - should download files
- [ ] Check that restored files appear in dashboard table
- [ ] Click "Sign Out Google" - should clear tokens
- [ ] Click "Backup" again - should prompt authentication again
- [ ] Verify progress bars update during backup/restore
- [ ] Verify notifications appear with correct summaries

---

## Support & Troubleshooting

For issues:
1. Check that `credentials.json` is in the correct location
2. Ensure you have an internet connection
3. Check that your Google account has sufficient storage space
4. Review error messages in notification dialogs
5. Check application logs in the console/terminal

