# Employee Food Pool Management System
### Encipher Health Private Limited

A Java Spring Boot application for managing employee food pool registration and collection.

---

## Features

- 🔐 **Zoho OAuth Login** - Employees login via Zoho People Plus
- 📝 **Food Pool Registration** - Register for Veg/Non-veg meals daily
- 📱 **QR Scanner** - Scan QR to collect food
- ⚙️ **Admin Dashboard** - Manage menu, view statistics
- 📊 **Daily Reports** - Track who registered and collected

---

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.2
- **Database**: MongoDB Atlas
- **Auth**: Zoho OAuth2
- **Frontend**: Thymeleaf, HTML/CSS/JS
- **Deployment**: Docker

---

## Prerequisites

- Java 17+
- Maven 3.9+
- MongoDB Atlas account (or local MongoDB)
- Zoho API credentials

---

## Quick Start

### 1. Clone and Configure

```bash
cd employee-food-management

# Update Zoho credentials in application.yml if needed
```

### 2. Run Locally

```bash
# Using Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/food-pool-management-1.0.0.jar
```

### 3. Access Application

Open: http://localhost:8888

---

## Docker Deployment

### Build and Run

```bash
# Build image
docker build -t encipher-food-pool .

# Run container
docker run -d -p 8888:8888 --name food-pool encipher-food-pool

# Or use docker-compose
docker-compose up -d
```

---

## Project Structure

```
employee-food-management/
├── src/main/java/com/encipher/foodpool/
│   ├── FoodPoolApplication.java      # Main entry point
│   ├── config/
│   │   └── SecurityConfig.java       # OAuth2 + Security config
│   ├── controller/
│   │   ├── MainController.java       # Main pages
│   │   ├── ApiController.java        # REST APIs
│   │   └── AdminController.java      # Admin pages
│   ├── model/
│   │   ├── Employee.java
│   │   ├── FoodPool.java
│   │   ├── FoodScan.java
│   │   └── MenuConfig.java
│   ├── repository/                   # MongoDB repositories
│   └── service/
│       ├── EmployeeService.java
│       └── FoodPoolService.java
├── src/main/resources/
│   ├── application.yml               # Configuration
│   └── templates/                    # Thymeleaf templates
├── scripts/
│   └── load_employees.py            # Load employees to MongoDB
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/pool` | Register for food pool |
| POST | `/api/scan` | Record food collection |
| GET | `/api/status` | Get today's status |
| POST | `/admin/menu/update` | Update menu (admin) |

---

## Zoho OAuth Setup

1. Go to [Zoho API Console](https://api-console.zoho.in/)
2. Create Server-based Application
3. Set redirect URI: `http://localhost:8888/login/oauth2/code/zoho`
4. Update `application.yml` with credentials

---

## MongoDB Collections

- `employees` - Employee master data
- `food_pools` - Daily food registrations
- `food_scans` - Food collection records
- `menu_config` - Daily menu settings

---

## Admin Access

To make an employee admin:
1. Login to the app
2. Go to Admin > Employees
3. Click "Make Admin" next to the employee

Or via script:
```bash
python3 scripts/load_employees.py employees.xlsx admin@email.com
```

---

© 2024 Encipher Health Private Limited

