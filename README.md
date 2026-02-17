# 🏥 DOCHELP - Healthcare Management System

<div align="center">

**A comprehensive Android healthcare management platform connecting patients and doctors**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Java](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.java.com/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-yellow.svg)](https://firebase.google.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 📋 Overview

DOCHELP is a modern healthcare management application designed to streamline communication and appointment management between patients and doctors. The app provides separate interfaces for both patients and healthcare providers, enabling efficient healthcare delivery and patient care management.

## ✨ Key Features

### 👥 Multi-Sector Support
- **Patient Portal**: Complete patient management with profile creation and medical history
- **Doctor Portal**: Professional dashboard for healthcare providers
- Secure authentication and role-based access control

### 📅 Appointment Management
- **Smart Scheduling**: Doctors can set and manage their availability
- **Weekly Schedules**: Intuitive calendar interface for appointment booking
- **Real-time Notifications**: Push notifications for appointment updates
- **Status Tracking**: Monitor appointments (Pending, Confirmed, Completed)

### 📄 Document Management
- **Medical Records Upload**: Patients can upload and store medical documents
- **OCR Text Recognition**: Automatic text extraction from medical documents using ML Kit
- **Secure Storage**: Firebase Storage integration for secure document handling
- **Document History**: Easy access to past medical records

### 🤖 AI-Powered Features
- **Appointment Notes Summarization**: AI-powered summaries using Hugging Face models
- **Smart SOS Alerts**: In-app audio alerts for urgent notifications (client-side implementation)
- **Intelligent Search**: Find doctors by specialty, location, or availability

### 💬 Communication
- **Doctor-Patient Messaging**: Direct communication channel for consultations
- **Appointment Notes**: Detailed notes and prescriptions for each visit
- **Notification System**: Real-time updates on appointment status and messages

### 📊 Analytics & Insights
- **Patient Dashboard**: View upcoming appointments, medical history, and doctor visits
- **Doctor Dashboard**: Manage patient list, view daily schedules, and analytics
- **Health Tracking**: Monitor patient health metrics and appointment history

## 🛠️ Technology Stack

### Core Technologies
- **Language**: Java 8
- **Platform**: Android (API 24+)
- **UI**: Material Design 3

### Backend & Database
- **Firebase Authentication**: Secure user authentication
- **Cloud Firestore**: Real-time NoSQL database
- **Firebase Storage**: Medical document storage
- **Firebase Cloud Messaging**: Push notifications

### AI & ML
- **Hugging Face API**: Text summarization for medical notes
- **ML Kit**: OCR for text recognition from documents

### Key Libraries
```gradle
• Firebase BOM (Platform)
• Material Components 1.12.0
• ML Kit Text Recognition 16.0.0
• AndroidX AppCompat
• Room Database (with annotation processing)
• Constraint Layout
```

## 📱 App Architecture

### Package Structure
```
com.example.dochelp/
├── activity/          # All UI activities
│   ├── SectorSelectionActivity
│   ├── PatientDashboardActivity
│   ├── DoctorDashboardActivity
│   ├── AppointmentNotesActivity
│   └── ...
├── adapter/           # RecyclerView adapters
├── model/             # Data models
│   ├── ApptItem
│   └── NoteItem
├── repository/        # Data layer
│   ├── AuthRepo
│   └── FirestoreRepo
├── session/           # Session management
│   ├── UserSession
│   └── AppointmentSession
├── summarizer/        # AI summarization
│   └── HFSummarizer
└── utils/             # Utility classes
    ├── KeyboardUtils
    ├── LoadingUtils
    └── GridSpacingItemDecoration
```

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest version recommended)
- JDK 8 or higher
- Android SDK (API 24 or higher)
- Firebase account
- Hugging Face API key (for summarization features)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/anonjj/DocHelp-Android.git
   cd DocHelp-Android
   ```

2. **Set up Firebase**
   - Create a new project in [Firebase Console](https://console.firebase.google.com/)
   - Download `google-services.json` and place it in the `app/` directory
   - Enable Authentication, Firestore, and Storage in Firebase Console

3. **Configure API Keys**
   
   Create a `local.properties` file in the root directory:
   ```properties
   sdk.dir=/path/to/Android/sdk
   HF_API_KEY=your_hugging_face_api_key_here
   ```

4. **Build and Run**
   - Open the project in Android Studio
   - Sync Gradle files
   - Run on an emulator or physical device (Android 7.0+)

### Firebase Setup

#### Firestore Database Structure
```
users/
  └── {userId}/
      ├── name
      ├── email
      ├── role (patient/doctor)
      ├── phone
      └── ... (role-specific fields)

appointments/
  └── {appointmentId}/
      ├── patientId
      ├── doctorId
      ├── date
      ├── time
      ├── status
      └── notes

doctors/
  └── {doctorId}/
      ├── specialty
      ├── availability[]
      └── ratings
```

#### Storage Structure
```
medical_documents/
  └── {userId}/
      └── {documentId}.pdf/jpg/png
```

#### Authentication
- Enable Email/Password authentication in Firebase Console
- Configure appropriate security rules for production

## 🎨 UI/UX Features

- **Material Design 3**: Modern, clean interface following Material Design guidelines
- **Gradient Themes**: Beautiful gradient backgrounds for enhanced visual appeal
- **Responsive Layouts**: Optimized for different screen sizes and orientations
- **Loading States**: Professional loading indicators and progress feedback
- **Dark Mode Support**: Theme compatibility with system dark mode settings
- **Accessibility**: Keyboard navigation and screen reader support

## 🔒 Security Features

- **Firebase Authentication**: Secure user authentication and authorization
- **Role-based Access**: Separate access levels for patients and doctors
- **Secure Storage**: Encrypted document storage in Firebase
- **Data Validation**: Input validation and sanitization
- **Network Security**: HTTPS-only communication

## 📊 App Permissions

Required permissions:
- `INTERNET` - Network communication with Firebase
- `ACCESS_NETWORK_STATE` - Check network connectivity
- `POST_NOTIFICATIONS` - Send push notifications (Android 13+)

## 🧪 Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

## 📦 Build

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Guidelines
- Follow Java coding conventions
- Add comments for complex logic
- Write unit tests for new features
- Update documentation as needed

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Jay Joshi**
- GitHub: [@anonjj](https://github.com/anonjj)

## 🙏 Acknowledgments

- Firebase for backend infrastructure
- Hugging Face for AI/ML capabilities
- ML Kit for text recognition
- Material Design team for design guidelines
- Open source community for various libraries

## 📧 Support

For issues, questions, or suggestions:
- Open an issue on [GitHub Issues](https://github.com/anonjj/DocHelp-Android/issues)
- Contact: [your-email@example.com]

## 🗺️ Roadmap

- [ ] Video consultation feature
- [ ] Prescription management system
- [ ] Medicine reminder notifications
- [ ] Health vitals tracking
- [ ] Multi-language support
- [ ] Telemedicine integration
- [ ] Insurance claim processing

---

<div align="center">

**Made with ❤️ for better healthcare**

⭐ Star this repo if you find it helpful!

</div>
