"use client"
import { useState } from 'react'
import { supabase } from '@/lib/supabase/client'
import { useRouter } from 'next/navigation'

export default function SignUp() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const handleSignUp = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault()
    setLoading(true)
    if (password !== confirmPassword) {
      setMessage('Passwords do not match')
      setLoading(false)
      return
    }
    const { data, error } = await supabase.auth.signUp({ email, password })
    setLoading(false)
    setMessage(error?.message || `Check ${data?.user?.email} for a confirmation link.`)
  }

  return (
    <div className="max-w-md mx-auto mt-16 p-6 bg-white rounded shadow">
      <h2 className="text-2xl mb-4 text-black">Sign Up</h2>
      <form>
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          className="w-full mb-2 p-2 border rounded text-black"
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={e => setPassword(e.target.value)}
          className="w-full mb-2 p-2 border rounded text-black"
        />

        <input
          type="text"
          placeholder="Confirm Password"
          value={confirmPassword}
          onChange={e => setConfirmPassword(e.target.value)}
          className="w-full mb-4 p-2 border rounded text-black"
        />

        <button
        type="button"
        onClick={handleSignUp}
        disabled={loading}
        className="flex-1 p-2 bg-blue-500 text-white rounded text-black"
        >
        Sign Up
        </button>
      </form>

      <button
        onClick={() => router.push('/signin')}
        className="mt-4 text-sm text-gray-600 underline text-black"
      >
        Sign in
      </button>

      {message && <p className="mt-4 text-center text-black">{message}</p>}
    </div>
  )
}
