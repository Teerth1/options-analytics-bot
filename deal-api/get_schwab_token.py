import http.server
import socketserver
import urllib.parse
import webbrowser
import requests
import base64
import time
import sys
import os

# Configuration - User will be prompted if env vars are missing
CLIENT_ID = os.environ.get('SCHWAB_CLIENT_ID')
CLIENT_SECRET = os.environ.get('SCHWAB_CLIENT_SECRET')
REDIRECT_URI = 'https://127.0.0.1'  # Standard for local apps
PORT = 80

# Prompt user if environment variables are not set
if not CLIENT_ID:
    print("⚠️  SCHWAB_CLIENT_ID not found in environment.")
    CLIENT_ID = input("Please enter your Schwab App Client Key (Consumer Key): ").strip()

if not CLIENT_SECRET:
    print("⚠️  SCHWAB_CLIENT_SECRET not found in environment.")
    CLIENT_SECRET = input("Please enter your Schwab App Client Secret: ").strip()

if not CLIENT_ID or not CLIENT_SECRET:
    print("❌ Error: Client ID and Client Secret are required to proceed.")
    sys.exit(1)

state = 'random_state_string'

def exchange_token(code):
    token_url = 'https://api.schwabapi.com/v1/oauth/token'
    headers = {
        'Authorization': f'Basic {base64.b64encode(f"{CLIENT_ID}:{CLIENT_SECRET}".encode()).decode()}',
        'Content-Type': 'application/x-www-form-urlencoded'
    }
    data = {
        'grant_type': 'authorization_code',
        'code': code,
        'redirect_uri': REDIRECT_URI
    }

    print("🔄 Exchanging code for tokens...")
    response = requests.post(token_url, headers=headers, data=data)

    if response.status_code == 200:
        tokens = response.json()
        print("\n" + "="*50)
        print("🎉 SUCCESS! HERE IS YOUR NEW REFRESH TOKEN:")
        print("="*50 + "\n")
        print(tokens['refresh_token'])
        print("\n" + "="*50)
        print("Copy the token above and paste it into the `update_token.ps1` script!")
    else:
        print(f"\n❌ Failed to get tokens: {response.status_code}")
        print(response.text)

def start_process():
    # Construct Authorization URL
    auth_url = (f"https://api.schwabapi.com/v1/oauth/authorize?"
                f"client_id={CLIENT_ID}&redirect_uri={REDIRECT_URI}&response_type=code")
    
    print(f"\n🚀 Opening browser to: {auth_url}")
    print(f"⚠️  NOTE: Ensure your Schwab App Callback URL is set to '{REDIRECT_URI}' in the developer portal!")
    
    webbrowser.open(auth_url)
    
    print("\n" + "-"*50)
    print("INSTRUCTIONS:")
    print("1. Log in to Schwab in the browser window that opened.")
    print("2. When you are redirected to 'https://127.0.0.1/...', the page may fail to load.")
    print("3. THAT IS OKAY! Copy the entire URL from your browser's address bar.")
    print("-"*50 + "\n")

    redirected_url = input("Paste the full redirected URL here: ").strip()
    
    try:
        parsed = urllib.parse.urlparse(redirected_url)
        query_params = urllib.parse.parse_qs(parsed.query)
        if 'code' in query_params:
            code = query_params['code'][0]
            extension = query_params.get('code')[0]
            # remove weird suffix if present (sometimes happens with copy paste)
            if '@' in code:
               code = code.split('@')[0] + '@' 
            
            print(f"✅ Extracted Authorization Code.")
            exchange_token(code)
        else:
            print("❌ Could not find 'code' parameter in the URL you pasted.")
    except Exception as e:
        print(f"❌ Error parsing URL: {e}")

if __name__ == '__main__':
    start_process()
