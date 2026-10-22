import { useState } from "react";
import roomApi from "../apis/roomApi";

export default function useCreateRoom() {
    const [form, setForm] = useState({
        departmentId: '',
        serviceGroupId: '',
        code: '',
        name: '',
        location: '',
        type: '',
    });

    const [error, setError] = useState(null)
    const [loading, setLoading] = useState(false)
    const handleChange = (e) => {
        const { name, value } = e.target.value

        setForm((prev) => ({ ...prev, [name]: value }))
    }

    const handleSubmit = async (e) => {
        e.prevenDefault()
        loading(true)
        try {
            const result = await roomApi.createRoom(form)

            return result
        } catch (error) {
            setError(error.message)
        } finally {
            setLoading(false);
        }

    }

    return {
        form,
        setForm,
        error,
        loading,
        handleChange,
        handleSubmit,
    }

}