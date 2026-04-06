
import os
import sys

try:
    import psycopg2
except ImportError:
    print("❌ psycopg2 module not found. Installing...")
    os.system(f"{sys.executable} -m pip install psycopg2-binary")
    import psycopg2

def reset_db_token():
    print("\n⚠️  WARNING: This will DELETE the current Schwab token from your database.")
    print("    This forces the bot to load the fresh token from your Environment Variables on next startup.\n")
    
    db_url = input("👉 Paste your Railway Public Database URL (postgresql://...): ").strip()
    
    if not db_url:
        print("❌ Database URL is required.")
        return

    try:
        conn = psycopg2.connect(db_url)
        cur = conn.cursor()
        
        print("⏳ Connecting to database...")
        cur.execute("DELETE FROM schwab_tokens;")
        deleted_count = cur.rowcount
        conn.commit()
        
        print(f"✅ SUCCESS! Deleted {deleted_count} stale token(s) from the database.")
        print("🚀 Now Restart your 'deal-api' service in Railway!")
        
        cur.close()
        conn.close()
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        print("Ensure you are using the PUBLIC Database URL (TCP Proxy) from Railway.")

if __name__ == "__main__":
    reset_db_token()
