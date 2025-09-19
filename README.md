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

```mermaid
flowchart TD
    subgraph Client["Client Apps"]
        A1["📱 Zoner Mobile (Android)"]
        A2["🌐 Web Client (Future)"]
    end

    subgraph Backend["Ktor Backend API"]
        B1["🔐 Authentication (JWT, OAuth)"]
        B2["👤 User Profiles & Follows"]
        B3["📝 Posts & Stories"]
        B4["💬 Messaging (WebSockets)"]
        B5["🔔 Notifications"]
        B6["🔍 Search & Discovery"]
        B7["🛡️ Privacy & Security"]
    end

    subgraph Database["PostgreSQL Database"]
        D1["Users"]
        D2["Posts"]
        D3["Messages"]
        D4["Follows"]
        D5["Likes & Comments"]
    end

    subgraph Storage["File Storage (S3 Compatible)"]
        S1["Images"]
        S2["Videos"]
        S3["Stories"]
    end

    Client -->|REST / WebSockets| Backend
    Backend --> Database
    Backend --> Storage
