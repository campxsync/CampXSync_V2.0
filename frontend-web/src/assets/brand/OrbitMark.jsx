/** CampXSync Orbit X mark — official brand symbol */
export default function OrbitMark({ className = '', title = 'CampXSync' }) {
  return (
    <svg
      className={className}
      viewBox="0 0 96 96"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={title}
    >
      <title>{title}</title>
      {/* Orbit ring (behind) */}
      <ellipse
        cx="48"
        cy="48"
        rx="41"
        ry="17"
        fill="none"
        stroke="#4D8CFF"
        strokeWidth="8"
        strokeLinecap="round"
        transform="rotate(-30 48 48)"
      />
      {/* Bold X */}
      <path
        fill="#FFFFFF"
        fillRule="evenodd"
        d="M22.5 14.5 48 44.2 73.5 14.5h14.8L55.4 48l33 33.5H73.5L48 51.8 22.5 81.5H7.7L40.6 48 7.7 14.5z"
      />
      {/* Front orbit arc for depth/motion */}
      <path
        d="M12 60c14 16 58 16 72 0"
        fill="none"
        stroke="#4D8CFF"
        strokeWidth="8"
        strokeLinecap="round"
        transform="rotate(-30 48 48)"
      />
    </svg>
  );
}
