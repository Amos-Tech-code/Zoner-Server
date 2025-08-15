# 📱 **Zoner App Backend – Ktor**

Welcome to the **Zoner App Backend**!  
This project is a robust backend built with **Ktor** and designed to power a **social media–like ecosystem** with features for content sharing, messaging, and real-time interactions.

---

## 🏗️ **Project Structure**

The backend follows a clean, modular, and scalable architecture:


---

## 🛠️ **Key Features**

### **1. Authentication**
- Email & Password sign-up/login with secure password hashing.
- Username uniqueness check with suggestion generator.
- OAuth support – Google and Facebook login.
- JWT-based Authentication – Secure, stateless sessions.

### **2. Profiles**
- Create and update personal profiles:
    - Display name
    - Username
    - Bio
    - Profile picture
    - Website & contact info
- Follow/unfollow other users.

### **3. Posts & Stories**
- Create posts with:
    - Text
    - Images
    - Videos
    - Multiple media items per post.
- Create disappearing stories with expiration timers.
- Like, comment, and share posts.
- View posts from followed users (home feed).

### **4. Messaging**
- Real-time 1-on-1 and group chats using WebSockets.
- Send text, images, and other media in chat.
- Typing indicators and message read receipts.

### **5. Notifications**
- Real-time push notifications for:
    - New followers
    - Likes, comments, mentions
    - Messages
    - Story views

### **6. Search & Discovery**
- Search by username, name, or hashtags.
- Explore trending business posts and suggested users.

### **7. Privacy & Security**
- Block/unblock users.
- Report inappropriate content.
- Account privacy settings (public/private).

---

## 🛠️ **Tech Stack**

**Backend**
- [Ktor](https://ktor.io) – Fast, lightweight Kotlin framework.
- [Exposed](https://github.com/JetBrains/Exposed) – ORM for type-safe DB queries.
- **Kotlin** – Concise and expressive language.

**Database**
- **PostgreSQL** – Reliable and scalable relational DB.
- Full-text search support for search features.

**Authentication**
- **JWT** – Secure, stateless authentication.
- **OAuth 2.0** – Google & Facebook login.

---

## 🚀 **Getting Started**

### **Prerequisites**
- Kotlin 1.8+
- PostgreSQL
- Gradle

### **Setup**
1. **Clone the repository**
   ```bash
   git clone https://github.com/Amos-Tech-code/Zoner-Server.git
   cd Zoner-Server


## 🏗️ **Architecture Overview**
   
   
                  ┌──────────────────────────┐
                  │        Client Apps        │
                  │  - Zoner Mobile (Android) │
                  │  - Web Client (Future)    │
                  └─────────────┬────────────┘
                                │ REST / WS
                                ▼
                ┌─────────────────────────────────┐
                │         Ktor Backend API         │
                │─────────────────────────────────│
                │  • Authentication (JWT, OAuth)   │
                │  • User Profiles & Follows       │
                │  • Posts & Stories               │
                │  • Messaging (WebSockets)        │
                │  • Notifications                 │
                │  • Search & Discovery            │
                │  • Privacy & Security            │
                └─────────────┬───────────────────┘
                                │
           ┌────────────────────┴─────────────────────┐
           │                                            │
┌─────────────────────┐                    ┌─────────────────────┐
│   PostgreSQL DB      │                    │   File Storage (S3) │
│  • Users              │                    │  • Images           │
│  • Posts              │                    │  • Videos           │
│  • Messages           │                    │  • Stories          │
│  • Follows            │                    └─────────────────────┘
│  • Likes/Comments     │
└───────────────────────┘

