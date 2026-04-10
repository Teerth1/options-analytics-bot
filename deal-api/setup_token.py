"""
ALL-IN-ONE Schwab Token Setup Script
=====================================
This script handles EVERYTHING:
1. Asks for your credentials
2. Opens the browser for login
3. Asks you to paste the redirect URL
4. Exchanges the code for tokens
5. Saves the token to schwab_tokens.json automatically

Just run: python setup_token.py
"""
import urllib.parse
import webbrowser
import requests
import base64
import json
import sys
import os
import re
import psycopg2

def load_env():
    env_vars = {}
    env_path = '.env'
    if os.path.exists(env_path):
        with open(env_path, 'r') as f:
            for line in f:
                if '=' in line:
                    key, value = line.split('=', 1)
                    env_vars[key.strip()] = value.strip().strip('"').strip("'")
    return env_vars

def update_database(refresh_token, access_token):
    env = load_env()
    host = env.get('PGHOST')
    port = env.get('PGPORT')
    dbname = env.get('PGDATABASE')
    user = env.get('PGUSER')
    password = env.get('PGPASSWORD')

    if not all([host, port, dbname, user, password]):
        print("\n⚠️  Missing database credentials in .env. Skipping cloud sync.")
        return

    print("\n🔄 Syncing tokens to Railway Postgres database...")
    try:
        conn = psycopg2.connect(
            host=host,
            port=port,
            database=dbname,
            user=user,
            password=password
        )
        cur = conn.cursor()
        
        # Current time in milliseconds
        import time
        now_ms = int(time.time() * 1000)
        
        # Update or Insert the "default" token row
        query = """
            INSERT INTO schwab_tokens (id, refresh_token, access_token, updated_at)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (id) DO UPDATE 
            SET refresh_token = EXCLUDED.refresh_token, 
                access_token = EXCLUDED.access_token, 
                updated_at = EXCLUDED.updated_at;
        """
        cur.execute(query, ("default", refresh_token, access_token, now_ms))
        conn.commit()
        cur.close()
        conn.close()
        print("✅ SUCCESS! Cloud database updated. Railway bot will pick up the new token automatically.")
    except Exception as e:
        print(f"❌ Database sync failed: {e}")

REDIRECT_URI = 'https://deal-aggregator-production.up.railway.app/auth/schwab/callback'

print("=" * 50)
print("  SCHWAB TOKEN SETUP (Auto-Detect .env)")
print("=" * 50)

# Step 1: Get credentials automatically
env = load_env()
client_id = env.get('SCHWAB_CLIENT_ID', '').strip()
client_secret = env.get('SCHWAB_CLIENT_SECRET', '').strip()

if not client_id or not client_secret:
    print("\n❌ Could not find SCHWAB_CLIENT_ID or SCHWAB_CLIENT_SECRET in your .env file!")
    print("Please add them to .env and try again.")
    sys.exit(1)

print(f"\n✅ Found credentials for App Key: {client_id[:4]}...{client_id[-4:]}")

# Step 2: Open browser
print("\n" + "=" * 50)
print("STEP 2: Log in to Schwab")
print("=" * 50)

auth_url = (f"https://api.schwabapi.com/v1/oauth/authorize?"
            f"client_id={client_id}&redirect_uri={REDIRECT_URI}&response_type=code")

print(f"\nOpening your browser...")
webbrowser.open(auth_url)

print("""
After logging in, your browser will redirect to a page that
WON'T LOAD (that's normal!). It will look something like:

  https://127.0.0.1/?code=XXXXXX&session=YYYYY

Copy that ENTIRE URL from your browser's address bar.
""")

# Step 3: Get the redirect URL
redirected_url = input("Paste the full URL here: ").strip()

# Step 4: Extract code and exchange
try:
    parsed = urllib.parse.urlparse(redirected_url)
    query_params = urllib.parse.parse_qs(parsed.query)
    
    if 'code' not in query_params:
        print("\n❌ Could not find 'code' in that URL. Make sure you copied the full URL.")
        sys.exit(1)
    
    code = query_params['code'][0]
    print(f"\n✅ Got authorization code!")
    
    # Exchange for tokens
    print("🔄 Exchanging for tokens...")
    
    auth_header = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    
    response = requests.post(
        'https://api.schwabapi.com/v1/oauth/token',
        headers={
            'Authorization': f'Basic {auth_header}',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        data={
            'grant_type': 'authorization_code',
            'code': code,
            'redirect_uri': REDIRECT_URI
        }
    )
    
    if response.status_code != 200:
        print(f"\n❌ Token exchange failed ({response.status_code})")
        print(f"Response: {response.text}")
        print("\nThe authorization code may have expired. Please run this script again.")
        sys.exit(1)
    
    tokens = response.json()
    refresh_token = tokens.get('refresh_token', '')
    access_token = tokens.get('access_token', '')
    
    if not refresh_token:
        print("\n❌ No refresh token in response!")
        print(f"Response: {response.text}")
        sys.exit(1)
    
    # Step 5: Save to file
    token_data = {
        'refresh_token': refresh_token,
        'access_token': access_token
    }
    
    with open('schwab_tokens.json', 'w') as f:
        json.dump(token_data, f, indent=2)
    
    # NEW: Sync to Railway Database
    update_database(refresh_token, access_token)
    
    print("\n" + "=" * 50)
    print("🎉 ALL DONE! Your bot is now updated locally AND in the cloud.")
    print("=" * 50)
    print("\n🚀 No further action needed. The Railway bot is now live with the new token!")

except Exception as e:
    print(f"\n❌ Error: {e}")
    print("Please try running this script again.")
    sys.exit(1)
