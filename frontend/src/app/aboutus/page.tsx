"use client";

import { useState } from "react";
import { colors } from "../themes";

export default function SignUp() {

  return (
    <div style={{ 
      backgroundColor: colors.primary.DEFAULT, 
      minHeight: "100vh",
      display: "flex",
      justifyContent: "center",
      alignItems: "center",
      flexDirection: "column",
      fontFamily: "Arial, Helvetica, sans-serif",
      }}>
      <div style={{marginBottom: "100px", textAlign: "center"}}>
      <h1>About Us</h1>
      <p> placeholder text </p>
      </div>
      <div style={{marginBottom: "100px", textAlign: "center"}}>
      <h1>Our Mission</h1>
      <p> placeholder text </p>
      </div>
    </div>
  );
}
