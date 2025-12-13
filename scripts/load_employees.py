#!/usr/bin/env python3
"""
Script to load employees from Excel to MongoDB
Usage: python load_employees.py /path/to/excel/file.xlsx
"""

import sys
import pandas as pd
from pymongo import MongoClient
from datetime import datetime

# MongoDB connection
MONGO_URI = "mongodb+srv://tharun_db_user:XplRhL6HZNdfwRol@encipherfoodmanagement.lpwcfvq.mongodb.net/food_management?retryWrites=true&w=majority"

# Admin list (names in lowercase for matching)
ADMIN_NAMES = ['tharun s', 'karthick m', 'george prasanna kumar w', 'ajay karthick a']

def load_employees(excel_path, clear_existing=True):
    """Load employees from Excel file to MongoDB"""
    
    # Connect to MongoDB
    print("Connecting to MongoDB...")
    client = MongoClient(MONGO_URI)
    db = client['food_management']
    employees_collection = db['employees']
    
    # Read Excel file
    print(f"Reading Excel file: {excel_path}")
    df = pd.read_excel(excel_path)
    
    # Display columns for debugging
    print(f"Columns found: {list(df.columns)}")
    print(f"Total rows: {len(df)}")
    
    if clear_existing:
        print("Clearing existing employees...")
        result = employees_collection.delete_many({})
        print(f"Deleted {result.deleted_count} existing records")
    
    # Process each row
    loaded = 0
    skipped = 0
    
    for idx, row in df.iterrows():
        try:
            # Get employee ID - handle different column names
            employee_id = None
            for col in ['Employee ID', 'Emp ID', 'EmployeeID', 'ID']:
                if col in df.columns:
                    employee_id = str(row[col]).strip()
                    break
            
            if not employee_id or employee_id == 'nan':
                # Try second column (index 1)
                employee_id = str(row.iloc[1]).strip() if len(row) > 1 else None
            
            # Get name
            name = None
            for col in ['Emp.Name', 'Name', 'Employee Name', 'EmployeeName']:
                if col in df.columns:
                    name = str(row[col]).strip()
                    break
            
            if not name or name == 'nan':
                # Try third column (index 2)
                name = str(row.iloc[2]).strip() if len(row) > 2 else None
            
            # Get email
            email = None
            for col in ['Email Id', 'Email', 'EmailID', 'email']:
                if col in df.columns:
                    email = str(row[col]).strip()
                    break
            
            if not email or email == 'nan':
                # Try fourth column (index 3)
                email = str(row.iloc[3]).strip() if len(row) > 3 else None
            
            # Skip if missing required fields
            if not employee_id or employee_id == 'nan' or not name or name == 'nan':
                print(f"Skipping row {idx}: Missing employee ID or name")
                skipped += 1
                continue
            
            # Check if admin
            is_admin = name.lower() in ADMIN_NAMES
            
            # Create employee document
            employee = {
                'employeeId': employee_id,
                'name': name,
                'email': email if email and email != 'nan' else None,
                'isAdmin': is_admin,
                'isActive': True,
                'dateOfJoining': None,
                '_class': 'com.encipher.foodpool.model.Employee'
            }
            
            # Insert or update
            employees_collection.update_one(
                {'employeeId': employee_id},
                {'$set': employee},
                upsert=True
            )
            
            admin_badge = " [ADMIN]" if is_admin else ""
            print(f"✓ Loaded: {employee_id} - {name}{admin_badge}")
            loaded += 1
            
        except Exception as e:
            print(f"Error processing row {idx}: {e}")
            skipped += 1
    
    print(f"\n{'='*50}")
    print(f"Summary:")
    print(f"  ✓ Loaded: {loaded}")
    print(f"  ⚠ Skipped: {skipped}")
    print(f"  📊 Total in DB: {employees_collection.count_documents({})}")
    print(f"  👑 Admins: {employees_collection.count_documents({'isAdmin': True})}")
    
    # List admins
    print(f"\nAdmins:")
    for admin in employees_collection.find({'isAdmin': True}):
        print(f"  - {admin['name']} ({admin['employeeId']})")
    
    client.close()
    print("\nDone!")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python load_employees.py /path/to/excel/file.xlsx")
        sys.exit(1)
    
    excel_path = sys.argv[1]
    load_employees(excel_path)
