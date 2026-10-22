import * as React from 'react'
import { cn } from '../../utils/utils'

/**
 * Reusable Button component inspired by shadcn/ui and WellNest design system.
 */
export const Button = React.forwardRef(
  (
    {
      className,
      variant = 'default',
      size = 'default',
      type = 'button',
      disabled = false,
      children,
      ...props
    },
    ref,
  ) => {
    const baseStyles =
      'inline-flex items-center justify-center font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-500 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 select-none cursor-pointer active:scale-[0.99]'

    const variants = {
      default:
        'bg-[#142534] text-white hover:bg-[#0f1d2a] shadow-sm hover:shadow active:bg-[#09131c]',
      mint:
        'bg-[#0d9488] text-white hover:bg-[#0f766e] shadow-sm hover:shadow-md active:bg-[#115e59]',
      secondary:
        'bg-[#e8faf6] text-[#0c7a6d] hover:bg-[#d4f7f0] active:bg-[#c1f3e9]',
      outline:
        'border border-[#d5e0dc] bg-white text-[#142534] hover:bg-[#f4f8f7] hover:border-[#cbd5e1]',
      ghost:
        'text-[#60737d] hover:text-[#142534] hover:bg-[#f4f8f7]',
      destructive:
        'bg-[#fdece8] text-[#d9534f] hover:bg-[#fadbd4] active:bg-[#f8cac1]',
    }

    const sizes = {
      default: 'h-11 px-5 py-2.5 rounded-xl text-sm',
      sm: 'h-9 px-3.5 rounded-lg text-xs',
      lg: 'h-12 px-6 rounded-xl text-base',
      pill: 'h-11 px-6 rounded-full text-sm font-semibold',
      icon: 'h-10 w-10 rounded-xl',
    }

    return (
      <button
        ref={ref}
        type={type}
        disabled={disabled}
        className={cn(baseStyles, variants[variant] || variants.default, sizes[size] || sizes.default, className)}
        {...props}
      >
        {children}
      </button>
    )
  },
)

Button.displayName = 'Button'
