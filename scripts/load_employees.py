#!/usr/bin/env python3
"""
Load employees from Excel spreadsheet to MongoDB
Run: python3 load_employees.py /path/to/employees.xlsx
"""

import sys
from pymongo import MongoClient
import pandas as pd
from datetime import datetime

# MongoDB connection
MONGO_URI = "mongodb+srv://tharun_db_user:XplRhL6HZNdfwRol@encipherfoodmanagement.lpwcfvq.mongodb.net/"
DB_NAME = "food_management"

def load_employees(excel_path, admin_emails=None):
    """Load employees from Excel to MongoDB"""
    
    if admin_emails is None:
        admin_emails = []
    
    # Connect to MongoDB
    client = MongoClient(MONGO_URI)
    db = client[DB_NAME]
    employees_col = db['employees']
    
    # Read Excel
    print(f"Reading: {excel_path}")
    df = pd.read_excel(excel_path, header=None)
    
    count = 0
    updated = 0
    
    # Data starts from row 2 (0-indexed)
    for idx in range(2, len(df)):
        emp_id = str(df.iloc[idx, 1]).strip() if pd.notna(df.iloc[idx, 1]) else None
        emp_name = str(df.iloc[idx, 2]).strip() if pd.notna(df.iloc[idx, 2]) else None
        doj = df.iloc[idx, 3] if pd.notna(df.iloc[idx, 3]) else None
        
        if not emp_id or not emp_name or emp_id == 'EMPLOYE ID':
            continue
        
        # Convert date
        doj_str = None
        if doj:
            try:
                if isinstance(doj, datetime):
                    doj_str = doj.strftime('%Y-%m-%d')
                else:
                    doj_str = str(doj)[:10]
            except:
                pass
        
        # Check if exists
        existing = employees_col.find_one({'employeeId': emp_id})
        
        if existing:
            # Update name if changed
            if existing.get('name') != emp_name:
                employees_col.update_one(
                    {'employeeId': emp_id},
                    {'$set': {'name': emp_name}}
                )
                updated += 1
        else:
            # Insert new
            employee = {
                'employeeId': emp_id,
                'name': emp_name,
                'email': None,
                'dateOfJoining': doj_str,
                'isAdmin': False,
                'isActive': True
            }
            employees_col.insert_one(employee)
            count += 1
            print(f"  Added: {emp_id} - {emp_name}")
    
    # Set admins
    for email in admin_emails:
        employees_col.update_many(
            {'email': email.lower()},
            {'$set': {'isAdmin': True}}
        )
    
    print(f"\n✅ Done!")
    print(f"   New employees: {count}")
    print(f"   Updated: {updated}")
    print(f"   Total in DB: {employees_col.count_documents({})}")
    
    client.close()

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 load_employees.py <excel_path> [admin_email1,admin_email2,...]")
        sys.exit(1)
    
    excel_path = sys.argv[1]
    admin_emails = sys.argv[2].split(',') if len(sys.argv) > 2 else []
    
    load_employees(excel_path, admin_emails)

